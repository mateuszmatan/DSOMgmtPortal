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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;

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
        Readings readings = read(monitored);
        Map<Long, List<PipelineEvidence>> byService = monitored.pipelines().stream()
                .collect(Collectors.groupingBy(view -> view.service().id(), Collectors.mapping(view -> {
                    PipelineRun run = readings.latest().of(view.metricsTag(), view.pipeline());
                    return pipeline(view, run, run == null ? null : readings.evidence().get(run),
                            monitored.platform());
                }, Collectors.toList())));
        List<ServiceEvidence> services = monitored.product().services().stream()
                .map(service -> new ServiceEvidence(service, byService.getOrDefault(service.id(), List.of())))
                .toList();
        return new ProductEvidence(monitored.product(), services, readings.error());
    }

    private Readings read(MonitoringTargets monitored) {
        MetricsReading<LatestRuns> latest = MetricsReading.of(
                () -> runs.latestRuns(monitored.tags(), monitored.sharedTags()), LatestRuns.none());
        if (latest.failed()) {
            return new Readings(latest.value(), Map.of(), latest.error());
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

    static PipelineEvidence pipeline(PipelineView view, PipelineRun run, RunEvidence recorded,
                                     PlatformSettings platform) {
        Pipeline pipeline = view.pipeline();
        RunResult status = RunResult.of(pipeline, run);
        if (run == null) {
            return new PipelineEvidence(pipeline, view.jenkinsJobUrl(), status, null);
        }
        ServiceSettings settings = view.service().settings();
        EvidenceLinks links = EvidenceLinks.of(view.buildUrl(run), platform.asocUrl(),
                settings.appScan().applicationId(),
                defaultIfBlank(trim(settings.sonar().serverUrl()), platform.sonarServerUrl()), settings.sonar().projectKey(),
                defaultIfBlank(trim(settings.nexusIq().serverUrl()), platform.nexusIqServerUrl()));
        RunEvidence points = recorded == null ? RunEvidence.none() : recorded;
        return new PipelineEvidence(pipeline, view.jenkinsJobUrl(), status,
                points.report(run, view.service().name(), links));
    }

    private record Readings(LatestRuns latest, Map<PipelineRun, RunEvidence> evidence, String error) {
    }
}
