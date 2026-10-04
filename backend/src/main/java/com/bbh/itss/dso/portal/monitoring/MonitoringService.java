package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.catalog.Product;
import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.common.InvalidRequestException;
import com.bbh.itss.dso.portal.common.NotFoundException;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.MonitoringStatus;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.Overview;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.PipelineHealth;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.PipelineMonitoring;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.ProductHealth;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.ProductMonitoring;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineRepository;
import com.bbh.itss.dso.portal.pipeline.PipelineResponse;
import com.bbh.itss.dso.portal.settings.GlobalSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MonitoringService {

    static final String NOT_CONFIGURED = "InfluxDB is not configured for the portal";
    static final int RECENT_RUNS = 25;
    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final Pattern RANGE = Pattern.compile("^([1-9][0-9]{0,2})d$");
    private static final int MAX_RANGE_DAYS = 730;

    private final ProductRepository products;
    private final PipelineRepository pipelines;
    private final PipelineMetricsRepository metrics;
    private final GrafanaPanels grafana;
    private final GlobalSettingsService settings;
    private final Clock clock;

    public MonitoringService(ProductRepository products, PipelineRepository pipelines,
                             PipelineMetricsRepository metrics, GrafanaPanels grafana, GlobalSettingsService settings,
                             Clock clock) {
        this.products = products;
        this.pipelines = pipelines;
        this.metrics = metrics;
        this.grafana = grafana;
        this.settings = settings;
        this.clock = clock;
    }

    public MonitoringStatus status() {
        String error = null;
        if (metrics.configured()) {
            error = read(() -> {
                metrics.ping();
                return null;
            }).error();
        }
        return new MonitoringStatus(metrics.configured(), metrics.configured() && error == null, error,
                grafana.configured(), grafana.configured() ? grafana.url() : null);
    }

    public Overview overview() {
        List<Pipeline> all = pipelines.findAllWithService();
        Reading<Map<MetricsTag, PipelineRun>> latest = latestRuns(all);
        Map<Long, List<Pipeline>> byProduct = all.stream()
                .collect(Collectors.groupingBy(p -> p.getService().getProduct().getId()));
        List<ProductHealth> health = products.findAllByOrderByNameAsc().stream()
                .map(product -> health(product, byProduct.getOrDefault(product.getId(), List.of()), latest.value()))
                .toList();
        return new Overview(health, latest.error());
    }

    public ProductMonitoring product(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        List<Pipeline> productPipelines = pipelines.findByProductId(productId);
        Reading<Map<MetricsTag, PipelineRun>> latest = latestRuns(productPipelines);
        String jenkinsUrl = settings.jenkinsUrl();
        List<PipelineHealth> health = productPipelines.stream()
                .map(pipeline -> {
                    PipelineRun run = latest.value().get(MetricsTag.of(pipeline));
                    return new PipelineHealth(PipelineResponse.summary(pipeline, jenkinsUrl), RunResult.of(pipeline, run),
                            run);
                })
                .toList();
        return new ProductMonitoring(product.getId(), product.getCode(), product.getName(), product.getDescription(),
                product.getOwnerTeam(), RunResult.worst(health.stream().map(PipelineHealth::status).toList()), health,
                latest.error());
    }

    public PipelineMonitoring pipeline(Long pipelineId, String range) {
        int days = rangeDays(range);
        Pipeline pipeline = pipelines.findWithServiceById(pipelineId)
                .orElseThrow(() -> NotFoundException.of("Pipeline", pipelineId));
        MetricsTag tag = MetricsTag.of(pipeline);

        Reading<List<PipelineRun>> recent = read(() -> metrics.recentRuns(tag, days, RECENT_RUNS));
        Reading<List<DoraPoint>> points = recent.error() != null ? new Reading<>(List.of(), recent.error())
                : read(() -> metrics.doraPoints(tag, days));
        List<PipelineRun> runs = recent.value() == null ? List.of() : recent.value();
        PipelineRun last = runs.isEmpty() ? null : runs.getFirst();
        if (last == null && points.error() == null) {
            last = latestRuns(List.of(pipeline)).value().get(tag);
        }
        DoraSummary dora = DoraCalculator.summarize(points.value() == null ? List.of() : points.value(), days,
                Instant.now(clock));
        return new PipelineMonitoring(PipelineResponse.withKeys(pipeline, settings.jenkinsUrl()),
                RunResult.of(pipeline, last), last, dora, runs, grafana.links(tag, days), points.error());
    }

    static int rangeDays(String range) {
        Matcher matcher = RANGE.matcher(range == null ? "" : range.trim());
        if (!matcher.matches()) {
            throw InvalidRequestException.of("range", "use a number of days such as 7d, 30d or 90d");
        }
        int days = Integer.parseInt(matcher.group(1));
        if (days > MAX_RANGE_DAYS) {
            throw InvalidRequestException.of("range", "can cover at most " + MAX_RANGE_DAYS + " days");
        }
        return days;
    }

    private static ProductHealth health(Product product, List<Pipeline> productPipelines,
                                        Map<MetricsTag, PipelineRun> latest) {
        Map<RunResult, Integer> counts = new EnumMap<>(RunResult.class);
        Instant lastRunAt = null;
        for (Pipeline pipeline : productPipelines) {
            PipelineRun run = latest.get(MetricsTag.of(pipeline));
            counts.merge(RunResult.of(pipeline, run), 1, Integer::sum);
            if (run != null && (lastRunAt == null || run.time().isAfter(lastRunAt))) {
                lastRunAt = run.time();
            }
        }
        return new ProductHealth(product.getId(), product.getCode(), product.getName(), product.getOwnerTeam(),
                product.getServices().size(), productPipelines.size(), RunResult.worst(counts.keySet()), counts,
                lastRunAt);
    }

    private Reading<Map<MetricsTag, PipelineRun>> latestRuns(List<Pipeline> forPipelines) {
        if (forPipelines.isEmpty()) {
            return new Reading<>(Map.of(), metrics.configured() ? null : NOT_CONFIGURED);
        }
        Reading<Map<MetricsTag, PipelineRun>> reading =
                read(() -> metrics.latestRuns(forPipelines.stream().map(MetricsTag::of).collect(Collectors.toSet())));
        return reading.value() == null ? new Reading<>(Map.of(), reading.error()) : reading;
    }

    private <T> Reading<T> read(Supplier<T> query) {
        if (!metrics.configured()) {
            return new Reading<>(null, NOT_CONFIGURED);
        }
        try {
            return new Reading<>(query.get(), null);
        } catch (RuntimeException e) {
            log.warn("Reading pipeline metrics from InfluxDB failed: {}", e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new Reading<>(null, "InfluxDB could not be read: "
                    + (message.length() > 300 ? message.substring(0, 300) : message));
        }
    }

    private record Reading<T>(T value, String error) {
    }
}
