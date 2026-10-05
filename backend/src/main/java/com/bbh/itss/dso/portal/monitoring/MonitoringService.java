package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.adapter.in.web.PipelineResponse;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.MonitoringStatus;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.Overview;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.PipelineHealth;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.PipelineMonitoring;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.ProductHealth;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.ProductMonitoring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final PipelineMetricsRepository metrics;
    private final GrafanaPanels grafana;
    private final ManageGlobalSettingsUseCase settings;
    private final Clock clock;

    public MonitoringService(ProductRepositoryPort products, PipelineRepositoryPort pipelines,
                             PipelineMetricsRepository metrics, GrafanaPanels grafana,
                             ManageGlobalSettingsUseCase settings, Clock clock) {
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
        List<Product> all = products.findAll();
        Map<Long, Product> byId = all.stream().collect(Collectors.toMap(Product::id, Function.identity()));
        List<PipelineView> views = pipelines.findAll().stream()
                .map(pipeline -> PipelineView.of(byId.get(pipeline.service().productId()), pipeline, null))
                .toList();
        Reading<Map<MetricsTag, PipelineRun>> latest = latestRuns(views);
        Map<Long, List<PipelineView>> byProduct = views.stream()
                .collect(Collectors.groupingBy(view -> view.product().id()));
        List<ProductHealth> health = all.stream()
                .map(product -> health(product, byProduct.getOrDefault(product.id(), List.of()), latest.value()))
                .toList();
        return new Overview(health, latest.error());
    }

    public ProductMonitoring product(Long productId) {
        Product product = products.load(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        String jenkinsUrl = settings.current().jenkinsUrl();
        List<PipelineView> productPipelines = pipelines.findByProductId(productId).stream()
                .map(pipeline -> PipelineView.of(product, pipeline, jenkinsUrl))
                .toList();
        Reading<Map<MetricsTag, PipelineRun>> latest = latestRuns(productPipelines);
        List<PipelineHealth> health = productPipelines.stream()
                .map(view -> {
                    PipelineRun run = latest.value().get(MetricsTag.of(view));
                    return new PipelineHealth(PipelineResponse.summary(view), RunResult.of(view.pipeline(), run), run);
                })
                .toList();
        return new ProductMonitoring(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), RunResult.worst(health.stream().map(PipelineHealth::status).toList()), health,
                latest.error());
    }

    public PipelineMonitoring pipeline(Long pipelineId, String range) {
        int days = rangeDays(range);
        Pipeline pipeline = pipelines.load(pipelineId)
                .orElseThrow(() -> NotFoundException.of("Pipeline", pipelineId));
        Product product = products.load(pipeline.service().productId())
                .orElseThrow(() -> NotFoundException.of("Product", pipeline.service().productId()));
        PipelineView view = PipelineView.of(product, pipeline, settings.current().jenkinsUrl());
        MetricsTag tag = MetricsTag.of(view);

        Reading<List<PipelineRun>> recent = read(() -> metrics.recentRuns(tag, days, RECENT_RUNS));
        Reading<List<DoraPoint>> points = recent.error() != null ? new Reading<>(List.of(), recent.error())
                : read(() -> metrics.doraPoints(tag, days));
        List<PipelineRun> runs = recent.value() == null ? List.of() : recent.value();
        PipelineRun last = runs.isEmpty() ? null : runs.getFirst();
        if (last == null && points.error() == null) {
            last = latestRuns(List.of(view)).value().get(tag);
        }
        DoraSummary dora = DoraCalculator.summarize(points.value() == null ? List.of() : points.value(), days,
                Instant.now(clock));
        return new PipelineMonitoring(PipelineResponse.withKeys(view), RunResult.of(pipeline, last), last, dora, runs,
                grafana.links(tag, days), points.error());
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

    private static ProductHealth health(Product product, List<PipelineView> productPipelines,
                                        Map<MetricsTag, PipelineRun> latest) {
        Map<RunResult, Integer> counts = new EnumMap<>(RunResult.class);
        Instant lastRunAt = null;
        for (PipelineView view : productPipelines) {
            PipelineRun run = latest.get(MetricsTag.of(view));
            counts.merge(RunResult.of(view.pipeline(), run), 1, Integer::sum);
            if (run != null && (lastRunAt == null || run.time().isAfter(lastRunAt))) {
                lastRunAt = run.time();
            }
        }
        return new ProductHealth(product.id(), product.code(), product.name(), product.ownerTeam(),
                product.services().size(), productPipelines.size(), RunResult.worst(counts.keySet()), counts,
                lastRunAt);
    }

    private Reading<Map<MetricsTag, PipelineRun>> latestRuns(List<PipelineView> forPipelines) {
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
