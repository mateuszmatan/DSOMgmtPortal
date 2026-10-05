package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.BuildEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.PipelineEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ProductEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.RunEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ServiceEvidence;
import com.bbh.itss.dso.portal.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.monitoring.PipelineMetricsRepository;
import com.bbh.itss.dso.portal.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.monitoring.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class EvidenceService {

    static final String NOT_CONFIGURED = "InfluxDB is not configured for the portal";
    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final PipelineMetricsRepository runs;
    private final RunEvidenceRepository evidence;
    private final ManageGlobalSettingsUseCase settings;

    public EvidenceService(ProductRepositoryPort products, PipelineRepositoryPort pipelines, PipelineMetricsRepository runs,
                           RunEvidenceRepository evidence, ManageGlobalSettingsUseCase settings) {
        this.products = products;
        this.pipelines = pipelines;
        this.runs = runs;
        this.evidence = evidence;
        this.settings = settings;
    }

    public ProductEvidence product(Long productId) {
        Product product = products.load(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        PlatformSettings platform = settings.current().platform();
        List<PipelineView> productPipelines = pipelines.findByProductId(productId).stream()
                .map(pipeline -> PipelineView.of(product, pipeline, platform.jenkinsUrl()))
                .toList();
        Map<Long, List<PipelineView>> byService = productPipelines.stream()
                .collect(Collectors.groupingBy(view -> view.service().id()));

        Map<MetricsTag, PipelineRun> latest = Map.of();
        Map<MetricsTag, RunPoints> points = Map.of();
        String error = runs.configured() ? null : NOT_CONFIGURED;
        if (error == null && !productPipelines.isEmpty()) {
            try {
                latest = runs.latestRuns(productPipelines.stream().map(MetricsTag::of).collect(Collectors.toSet()));
                points = evidence.pointsOf(latest);
            } catch (RuntimeException e) {
                log.warn("Reading run evidence from InfluxDB failed: {}", e.getMessage());
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                error = "InfluxDB could not be read: " + (message.length() > 300 ? message.substring(0, 300) : message);
                latest = Map.of();
                points = Map.of();
            }
        }

        Map<MetricsTag, PipelineRun> latestRuns = latest;
        Map<MetricsTag, RunPoints> runPoints = points;
        List<ServiceEvidence> services = product.services().stream()
                .map(service -> service(service, byService.getOrDefault(service.id(), List.of()), latestRuns,
                        runPoints, platform))
                .toList();
        return new ProductEvidence(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(), services, error);
    }

    private static ServiceEvidence service(Service service, List<PipelineView> servicePipelines,
                                           Map<MetricsTag, PipelineRun> latest, Map<MetricsTag, RunPoints> points,
                                           PlatformSettings platform) {
        List<PipelineEvidence> pipelineEvidence = servicePipelines.stream()
                .map(view -> pipeline(view, latest.get(MetricsTag.of(view)), points.get(MetricsTag.of(view)), platform))
                .toList();
        ServiceSettings settings = service.settings();
        return new ServiceEvidence(service.id(), service.name(), service.description(),
                settings.scm().repositoryUrl(), settings.deployment().artifactName(),
                settings.appScan().applicationId(), settings.sonar().projectKey(),
                settings.nexusIq().application(), pipelineEvidence);
    }

    static PipelineEvidence pipeline(PipelineView view, PipelineRun run, RunPoints points, PlatformSettings platform) {
        Pipeline pipeline = view.pipeline();
        String jobUrl = pipeline.settings().jenkinsJobUrl(platform.jenkinsUrl());
        RunResult status = RunResult.of(pipeline, run);
        if (run == null) {
            return new PipelineEvidence(pipeline.id(), pipeline.type(), pipeline.isEnabled(), jobUrl, status, null);
        }
        String buildJobUrl = jobUrl != null ? jobUrl : PipelineSettings.jobUrl(run.job(), platform.jenkinsUrl());
        ServiceSettings settings = view.service().settings();
        EvidenceLinks links = EvidenceLinks.of(buildJobUrl, run.build(), platform.asocUrl(),
                settings.appScan().applicationId(), platform.sonarServerUrl(), settings.sonar().projectKey(),
                platform.nexusIqServerUrl());
        RunPoints recorded = points == null ? new RunPoints(List.of()) : points;
        String module = view.service().name();
        BuildEvidence build = new BuildEvidence(run.build(), run.time(), run.result(), run.branch(), run.commit(),
                run.durationSeconds(), run.job(), links.buildUrl(), links.reportUrl(), links.testReportUrl(),
                links.artifactsUrl());
        RunEvidence evidence = new RunEvidence(build, recorded.coverage(module), recorded.testSuites(module),
                recorded.scans(module, links), recorded.releaseGate(), recorded.stages());
        return new PipelineEvidence(pipeline.id(), pipeline.type(), pipeline.isEnabled(), jobUrl, status, evidence);
    }
}
