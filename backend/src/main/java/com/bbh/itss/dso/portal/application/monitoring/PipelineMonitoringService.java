package com.bbh.itss.dso.portal.application.monitoring;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringOverview;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringStatus;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineHealth;
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineMonitoring;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductHealth;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductMonitoring;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort;
import com.bbh.itss.dso.portal.application.monitoring.port.out.MonitoringStatusPort;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.DoraCalculator;
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsReading;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.MonitoringRange;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class PipelineMonitoringService implements MonitorPipelinesUseCase {

    static final int RECENT_RUNS = 25;

    private final ReadMonitoringTargetsUseCase targets;
    private final PipelineRunsPort runs;
    private final MonitoringStatusPort metrics;
    private final DashboardLinksPort dashboards;
    private final Clock clock;

    public PipelineMonitoringService(ReadMonitoringTargetsUseCase targets, PipelineRunsPort runs,
                                     MonitoringStatusPort metrics, DashboardLinksPort dashboards, Clock clock) {
        this.targets = targets;
        this.runs = runs;
        this.metrics = metrics;
        this.dashboards = dashboards;
        this.clock = clock;
    }

    @Override
    @WithoutTransaction
    public MonitoringStatus status() {
        boolean configured = metrics.configured();
        String error = configured ? MetricsReading.of(this::ping, false).error() : null;
        Optional<String> dashboard = dashboards.url();
        return new MonitoringStatus(configured, configured && error == null, error, dashboard.isPresent(),
                dashboard.orElse(null));
    }

    @Override
    @WithoutTransaction
    public MonitoringOverview overview() {
        MonitoringTargets monitored = targets.everything();
        List<PipelineView> views = monitored.pipelines();
        MetricsReading<Map<MetricsTag, PipelineRun>> latest = latestRuns(views);
        Map<Long, List<PipelineView>> byProduct = views.stream()
                .collect(Collectors.groupingBy(view -> view.product().id()));
        List<ProductHealth> health = monitored.products().stream()
                .map(product -> health(product, byProduct.getOrDefault(product.id(), List.of()), latest.value()))
                .toList();
        return new MonitoringOverview(health, latest.error());
    }

    @Override
    @WithoutTransaction
    public ProductMonitoring product(long productId) {
        MonitoringTargets monitored = targets.ofProduct(productId);
        Product product = monitored.product();
        List<PipelineView> productPipelines = monitored.pipelines();
        MetricsReading<Map<MetricsTag, PipelineRun>> latest = latestRuns(productPipelines);
        List<PipelineHealth> health = productPipelines.stream()
                .map(view -> {
                    PipelineRun run = latest.value().get(tag(view));
                    return new PipelineHealth(view, RunResult.of(view.pipeline(), run), run);
                })
                .toList();
        return new ProductMonitoring(product, RunResult.worst(health.stream().map(PipelineHealth::status).toList()),
                health, latest.error());
    }

    @Override
    @WithoutTransaction
    public PipelineMonitoring pipeline(long pipelineId, String range) {
        int days = MonitoringRange.parse(range).days();
        PipelineView view = targets.ofPipeline(pipelineId).pipeline();
        Pipeline pipeline = view.pipeline();
        MetricsTag tag = tag(view);

        MetricsReading<List<PipelineRun>> recent = MetricsReading.of(() -> runs.recentRuns(tag, days, RECENT_RUNS),
                List.of());
        MetricsReading<List<DoraPoint>> points = recent.failed()
                ? MetricsReading.unavailable(List.of(), recent.error())
                : MetricsReading.of(() -> runs.doraPoints(tag, days), List.of());
        List<PipelineRun> recentRuns = recent.value();
        PipelineRun last = recentRuns.isEmpty() ? null : recentRuns.getFirst();
        if (last == null && !points.failed()) {
            last = latestRuns(List.of(view)).value().get(tag);
        }
        DoraSummary dora = DoraCalculator.summarize(points.value(), days, Instant.now(clock));
        return new PipelineMonitoring(view, RunResult.of(pipeline, last), last, dora, recentRuns,
                dashboards.links(tag, days).orElse(null), points.error());
    }

    private boolean ping() {
        metrics.ping();
        return true;
    }

    private MetricsReading<Map<MetricsTag, PipelineRun>> latestRuns(List<PipelineView> views) {
        Set<MetricsTag> tags = views.stream().map(PipelineMonitoringService::tag).collect(Collectors.toSet());
        return MetricsReading.of(() -> runs.latestRuns(tags), Map.of());
    }

    private static ProductHealth health(Product product, List<PipelineView> productPipelines,
                                        Map<MetricsTag, PipelineRun> latest) {
        Map<RunResult, Integer> counts = new EnumMap<>(RunResult.class);
        Instant lastRunAt = null;
        for (PipelineView view : productPipelines) {
            PipelineRun run = latest.get(tag(view));
            counts.merge(RunResult.of(view.pipeline(), run), 1, Integer::sum);
            if (run != null && (lastRunAt == null || run.time().isAfter(lastRunAt))) {
                lastRunAt = run.time();
            }
        }
        return new ProductHealth(product, productPipelines.size(), RunResult.worst(counts.keySet()), counts,
                lastRunAt);
    }

    private static MetricsTag tag(PipelineView view) {
        return MetricsTag.of(view.service(), view.pipeline());
    }
}
