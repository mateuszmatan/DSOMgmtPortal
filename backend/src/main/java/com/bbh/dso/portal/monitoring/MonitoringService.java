package com.bbh.dso.portal.monitoring;

import com.bbh.dso.portal.catalog.Product;
import com.bbh.dso.portal.catalog.ProductRepository;
import com.bbh.dso.portal.common.InvalidRequestException;
import com.bbh.dso.portal.common.NotFoundException;
import com.bbh.dso.portal.monitoring.MonitoringDtos.GrafanaLinks;
import com.bbh.dso.portal.monitoring.MonitoringDtos.GrafanaPanel;
import com.bbh.dso.portal.monitoring.MonitoringDtos.MonitoringStatus;
import com.bbh.dso.portal.monitoring.MonitoringDtos.Overview;
import com.bbh.dso.portal.monitoring.MonitoringDtos.PipelineHealth;
import com.bbh.dso.portal.monitoring.MonitoringDtos.PipelineMonitoring;
import com.bbh.dso.portal.monitoring.MonitoringDtos.ProductHealth;
import com.bbh.dso.portal.monitoring.MonitoringDtos.ProductMonitoring;
import com.bbh.dso.portal.pipeline.Pipeline;
import com.bbh.dso.portal.pipeline.PipelineRepository;
import com.bbh.dso.portal.pipeline.PipelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Combines the pipelines stored in the portal with the runs the DevSecOps library writes to InfluxDB.
 * A pipeline's runs are found by the tags the library writes: {@code project} (influx.project plus the
 * pipeline type suffix) and {@code env}.
 */
@Service
@Transactional(readOnly = true)
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final Pattern RANGE = Pattern.compile("^([1-9][0-9]{0,2})d$");
    private static final int RECENT_RUNS = 25;

    /** Order of severity used to summarise several pipelines in one status. */
    private static final List<RunResult> SEVERITY = List.of(RunResult.FAILURE, RunResult.UNSTABLE, RunResult.ABORTED,
            RunResult.NOT_BUILT, RunResult.SUCCESS, RunResult.NO_DATA, RunResult.DISABLED);

    private final ProductRepository products;
    private final PipelineRepository pipelines;
    private final InfluxQueryClient influx;
    private final InfluxProperties influxProperties;
    private final GrafanaProperties grafana;

    public MonitoringService(ProductRepository products, PipelineRepository pipelines, InfluxQueryClient influx,
                             InfluxProperties influxProperties, GrafanaProperties grafana) {
        this.products = products;
        this.pipelines = pipelines;
        this.influx = influx;
        this.influxProperties = influxProperties;
        this.grafana = grafana;
    }

    public MonitoringStatus status() {
        boolean reachable = false;
        String error = null;
        if (influx.configured()) {
            try {
                influx.query("buckets() |> limit(n: 1)");
                reachable = true;
            } catch (RuntimeException e) {
                error = describe(e);
            }
        }
        return new MonitoringStatus(influx.configured(), reachable, error, grafana.configured(),
                grafana.configured() ? grafana.url() : null);
    }

    public Overview overview() {
        List<Pipeline> all = pipelines.findAllWithService();
        LatestRuns latest = latestRuns(all);
        Map<Long, List<Pipeline>> byProduct = all.stream()
                .collect(Collectors.groupingBy(p -> p.getService().getProduct().getId()));
        List<ProductHealth> health = new ArrayList<>();
        for (Product product : products.findAllByOrderByNameAsc()) {
            Map<RunResult, Integer> counts = new EnumMap<>(RunResult.class);
            Instant lastRunAt = null;
            for (Pipeline pipeline : byProduct.getOrDefault(product.getId(), List.of())) {
                PipelineRun run = latest.runs().get(pipeline.getId());
                counts.merge(statusOf(pipeline, run), 1, Integer::sum);
                if (run != null && (lastRunAt == null || run.time().isAfter(lastRunAt))) {
                    lastRunAt = run.time();
                }
            }
            health.add(new ProductHealth(product.getId(), product.getCode(), product.getName(), product.getOwnerTeam(),
                    product.getServices().size(), counts.values().stream().mapToInt(Integer::intValue).sum(),
                    worst(counts.keySet().stream().toList()), counts, lastRunAt));
        }
        return new Overview(health, latest.error());
    }

    public ProductMonitoring product(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        List<Pipeline> productPipelines = pipelines.findByProductId(productId);
        LatestRuns latest = latestRuns(productPipelines);
        List<PipelineHealth> health = productPipelines.stream()
                .map(p -> {
                    PipelineRun run = latest.runs().get(p.getId());
                    return new PipelineHealth(PipelineResponse.summary(p), statusOf(p, run), run);
                })
                .toList();
        return new ProductMonitoring(product.getId(), product.getCode(), product.getName(), product.getDescription(),
                product.getOwnerTeam(), worst(health.stream().map(PipelineHealth::status).toList()), health,
                latest.error());
    }

    public PipelineMonitoring pipeline(Long pipelineId, String range) {
        int rangeDays = rangeDays(range);
        Pipeline pipeline = pipelines.findWithServiceById(pipelineId)
                .orElseThrow(() -> NotFoundException.of("Pipeline", pipelineId));
        String tag = projectTag(pipeline);
        String env = pipeline.getService().getInfluxEnv();

        List<PipelineRun> recent = List.of();
        DoraSummary dora = DoraCalculator.summarize(List.of(), rangeDays, Instant.now());
        String error = influx.configured() ? null : "InfluxDB is not configured for the portal";
        if (influx.configured()) {
            try {
                recent = influx.query(recentRunsQuery(tag, env, rangeDays)).stream().map(PipelineRun::fromRow)
                        .sorted(Comparator.comparing(PipelineRun::time).reversed()).toList();
                dora = DoraCalculator.summarize(influx.query(doraQuery(tag, env, rangeDays)).stream()
                        .map(MonitoringService::doraPoint).filter(Objects::nonNull).toList(), rangeDays, Instant.now());
            } catch (RuntimeException e) {
                log.warn("Reading the metrics of pipeline {} failed: {}", pipelineId, e.getMessage());
                error = describe(e);
            }
        }
        PipelineRun last = recent.isEmpty() ? null : recent.getFirst();
        if (last == null && error == null) {
            last = latestRuns(List.of(pipeline)).runs().get(pipeline.getId());
        }
        return new PipelineMonitoring(PipelineResponse.withKeys(pipeline), statusOf(pipeline, last), last, dora, recent,
                grafanaLinks(pipeline, rangeDays), error);
    }

    static int rangeDays(String range) {
        var matcher = RANGE.matcher(range == null ? "" : range.trim());
        if (!matcher.matches()) {
            throw InvalidRequestException.of("range", "use a number of days such as 7d, 30d or 90d");
        }
        int days = Integer.parseInt(matcher.group(1));
        if (days > 730) {
            throw InvalidRequestException.of("range", "can cover at most 730 days");
        }
        return days;
    }

    private GrafanaLinks grafanaLinks(Pipeline pipeline, int rangeDays) {
        if (!grafana.configured()) {
            return null;
        }
        String base = grafana.url().replaceAll("/+$", "");
        Map<String, String> query = new java.util.LinkedHashMap<>();
        query.put("orgId", String.valueOf(grafana.orgId()));
        query.put("var-project", projectTag(pipeline));
        query.put("var-env", pipeline.getService().getInfluxEnv());
        query.put("var-variant", pipeline.getType().variant());
        query.put("from", "now-" + rangeDays + "d");
        query.put("to", "now");
        query.put("theme", grafana.theme());

        String dashboardUrl = url(base + "/d/" + grafana.dashboardUid() + "/" + grafana.dashboardSlug(), query, null);
        List<GrafanaPanel> panels = grafana.panels().stream()
                .map(panel -> new GrafanaPanel(panel.id(), panel.title(), panel.width(),
                        url(base + "/d-solo/" + grafana.dashboardUid() + "/" + grafana.dashboardSlug(), query, panel.id())))
                .toList();
        return new GrafanaLinks(dashboardUrl, panels);
    }

    private static String url(String path, Map<String, String> query, Integer panelId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        query.forEach(builder::queryParam);
        if (panelId != null) {
            builder.queryParam("panelId", panelId);
        }
        return builder.encode().build().toUriString();
    }

    private LatestRuns latestRuns(List<Pipeline> forPipelines) {
        if (forPipelines.isEmpty() || !influx.configured()) {
            return new LatestRuns(Map.of(), influx.configured() ? null : "InfluxDB is not configured for the portal");
        }
        try {
            Map<String, PipelineRun> byTag = new HashMap<>();
            for (Map<String, String> row : influx.query(latestRunsQuery(forPipelines))) {
                byTag.put(row.get("project") + "|" + row.get("env"), PipelineRun.fromRow(row));
            }
            Map<Long, PipelineRun> runs = new HashMap<>();
            for (Pipeline pipeline : forPipelines) {
                PipelineRun run = byTag.get(projectTag(pipeline) + "|" + pipeline.getService().getInfluxEnv());
                if (run != null) {
                    runs.put(pipeline.getId(), run);
                }
            }
            return new LatestRuns(runs, null);
        } catch (RuntimeException e) {
            log.warn("Reading the latest pipeline runs failed: {}", e.getMessage());
            return new LatestRuns(Map.of(), describe(e));
        }
    }

    private String latestRunsQuery(List<Pipeline> forPipelines) {
        String tags = forPipelines.stream().map(MonitoringService::projectTag).distinct()
                .map(InfluxQueryClient::literal).collect(Collectors.joining(", "));
        // last() per series first: result and branch are tags, so each outcome is its own series.
        return """
                from(bucket: %s)
                  |> range(start: -%s)
                  |> filter(fn: (r) => r._measurement == "pipeline_run")
                  |> filter(fn: (r) => contains(value: r.project, set: [%s]))
                  |> last()
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group(columns: ["project", "env"])
                  |> sort(columns: ["_time"])
                  |> last(column: "_time")
                """.formatted(InfluxQueryClient.literal(influx.bucket()), influxProperties.lastRunLookback(), tags);
    }

    private String recentRunsQuery(String tag, String env, int rangeDays) {
        return """
                from(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "pipeline_run")
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"], desc: true)
                  |> limit(n: %d)
                """.formatted(InfluxQueryClient.literal(influx.bucket()), rangeDays, InfluxQueryClient.literal(tag),
                InfluxQueryClient.literal(env), RECENT_RUNS);
    }

    private String doraQuery(String tag, String env, int rangeDays) {
        return """
                from(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "dora")
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> filter(fn: (r) => r._field == "deployment" or r._field == "change_failure" or r._field == "lead_time_s" or r._field == "duration_s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"])
                """.formatted(InfluxQueryClient.literal(influx.bucket()), rangeDays, InfluxQueryClient.literal(tag),
                InfluxQueryClient.literal(env));
    }

    private static DoraPoint doraPoint(Map<String, String> row) {
        if (row.get("_time") == null) {
            return null;
        }
        return new DoraPoint(Instant.parse(row.get("_time")), positive(row.get("deployment")),
                positive(row.get("change_failure")), orZero(PipelineRun.number(row.get("lead_time_s"))),
                orZero(PipelineRun.number(row.get("duration_s"))));
    }

    private static boolean positive(String value) {
        Long number = PipelineRun.number(value);
        return number != null && number > 0;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }

    private static String projectTag(Pipeline pipeline) {
        return pipeline.getType().influxProjectTag(pipeline.getService().getInfluxProject());
    }

    private static RunResult statusOf(Pipeline pipeline, PipelineRun run) {
        if (pipeline.activeKey().isEmpty()) {
            return RunResult.DISABLED;
        }
        return run == null ? RunResult.NO_DATA : run.result();
    }

    private static RunResult worst(List<RunResult> statuses) {
        return SEVERITY.stream().filter(statuses::contains).findFirst().orElse(RunResult.NO_DATA);
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "InfluxDB could not be read: " + (message.length() > 300 ? message.substring(0, 300) : message);
    }

    private record LatestRuns(Map<Long, PipelineRun> runs, String error) {
    }
}
