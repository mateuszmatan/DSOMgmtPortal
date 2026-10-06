package com.bbh.itss.dso.portal.application.evidence;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.evidence.port.in.PipelineEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.ProductEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase;
import com.bbh.itss.dso.portal.application.evidence.port.in.ServiceEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.evidence.EvidenceLinks;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsReading;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class ChangeEvidenceService implements QueryEvidenceUseCase {

    private final ReadMonitoringTargetsUseCase targets;
    private final PipelineRunsPort runs;
    private final RunEvidencePort evidence;

    public ChangeEvidenceService(ReadMonitoringTargetsUseCase targets, PipelineRunsPort runs,
                                 RunEvidencePort evidence) {
        this.targets = targets;
        this.runs = runs;
        this.evidence = evidence;
    }

    @Override
    @WithoutTransaction
    public ProductEvidence product(long productId) {
        MonitoringTargets monitored = targets.ofProduct(productId);
        Product product = monitored.product();
        PlatformSettings platform = monitored.platform();
        Map<Long, List<Pipeline>> byService = monitored.pipelines().stream()
                .collect(Collectors.groupingBy(view -> view.service().id(),
                        Collectors.mapping(PipelineView::pipeline, Collectors.toList())));
        Readings readings = read(monitored);
        List<ServiceEvidence> services = product.services().stream()
                .map(service -> service(service, byService.getOrDefault(service.id(), List.of()), readings, platform))
                .toList();
        return new ProductEvidence(product, services, readings.error());
    }

    private Readings read(MonitoringTargets monitored) {
        MetricsReading<LatestRuns> latest = MetricsReading.of(
                () -> runs.latestRuns(monitored.tags(), monitored.sharedTags()), LatestRuns.none());
        if (latest.failed()) {
            return new Readings(LatestRuns.none(), Map.of(), latest.error());
        }
        Map<MetricsTag, Set<PipelineRun>> attributed = new HashMap<>();
        for (PipelineView view : monitored.pipelines()) {
            PipelineRun run = latest.value().of(view.metricsTag(), view.pipeline());
            if (run != null) {
                attributed.computeIfAbsent(view.metricsTag(), tag -> new HashSet<>()).add(run);
            }
        }
        MetricsReading<Map<PipelineRun, RunEvidence>> recorded =
                MetricsReading.of(() -> evidence.evidenceOf(attributed), Map.of());
        return new Readings(latest.value(), recorded.value(), recorded.error());
    }

    private static ServiceEvidence service(Service service, List<Pipeline> servicePipelines, Readings readings,
                                           PlatformSettings platform) {
        return new ServiceEvidence(service, servicePipelines.stream()
                .map(pipeline -> {
                    PipelineRun run = readings.latest().of(MetricsTag.of(service, pipeline), pipeline);
                    return pipeline(service, pipeline, run, run == null ? null : readings.evidence().get(run),
                            platform);
                })
                .toList());
    }

    static PipelineEvidence pipeline(Service service, Pipeline pipeline, PipelineRun run, RunEvidence recorded,
                                     PlatformSettings platform) {
        String jobUrl = pipeline.settings().jenkinsJobUrl(platform.jenkinsUrl());
        RunResult status = RunResult.of(pipeline, run);
        if (run == null) {
            return new PipelineEvidence(pipeline, jobUrl, status, null);
        }
        ServiceSettings settings = service.settings();
        EvidenceLinks links = EvidenceLinks.of(run.buildUrl(platform.jenkinsUrl(), pipeline.settings().jenkinsJob()),
                platform.asocUrl(),
                settings.appScan().applicationId(),
                Text.orDefault(settings.sonar().serverUrl(), platform.sonarServerUrl()), settings.sonar().projectKey(),
                Text.orDefault(settings.nexusIq().serverUrl(), platform.nexusIqServerUrl()));
        RunEvidence points = recorded == null ? RunEvidence.none() : recorded;
        return new PipelineEvidence(pipeline, jobUrl, status, points.report(run, service.name(), links));
    }

    private record Readings(LatestRuns latest, Map<PipelineRun, RunEvidence> evidence, String error) {
    }
}
