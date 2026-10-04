package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.catalog.Product;
import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.catalog.ServiceDefinition;
import com.bbh.itss.dso.portal.common.NotFoundException;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.BuildEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.PipelineEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ProductEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.RunEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ServiceEvidence;
import com.bbh.itss.dso.portal.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.monitoring.PipelineMetricsRepository;
import com.bbh.itss.dso.portal.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.monitoring.RunResult;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineRepository;
import com.bbh.itss.dso.portal.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.settings.GlobalSettingsService;
import com.bbh.itss.dso.portal.settings.PlatformSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EvidenceService {

    static final String NOT_CONFIGURED = "InfluxDB is not configured for the portal";
    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final ProductRepository products;
    private final PipelineRepository pipelines;
    private final PipelineMetricsRepository runs;
    private final RunEvidenceRepository evidence;
    private final GlobalSettingsService settings;

    public EvidenceService(ProductRepository products, PipelineRepository pipelines, PipelineMetricsRepository runs,
                           RunEvidenceRepository evidence, GlobalSettingsService settings) {
        this.products = products;
        this.pipelines = pipelines;
        this.runs = runs;
        this.evidence = evidence;
        this.settings = settings;
    }

    public ProductEvidence product(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        List<Pipeline> productPipelines = pipelines.findByProductId(productId);
        Map<Long, List<Pipeline>> byService = productPipelines.stream()
                .collect(Collectors.groupingBy(pipeline -> pipeline.getService().getId()));

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

        PlatformSettings platform = settings.values().platform();
        Map<MetricsTag, PipelineRun> latestRuns = latest;
        Map<MetricsTag, RunPoints> runPoints = points;
        List<ServiceEvidence> services = product.getServices().stream()
                .map(service -> service(service, byService.getOrDefault(service.getId(), List.of()), latestRuns,
                        runPoints, platform))
                .toList();
        return new ProductEvidence(product.getId(), product.getCode(), product.getName(), product.getDescription(),
                product.getOwnerTeam(), product.getContactEmail(), services, error);
    }

    private static ServiceEvidence service(ServiceDefinition service, List<Pipeline> servicePipelines,
                                           Map<MetricsTag, PipelineRun> latest, Map<MetricsTag, RunPoints> points,
                                           PlatformSettings platform) {
        List<PipelineEvidence> pipelineEvidence = servicePipelines.stream()
                .map(pipeline -> pipeline(pipeline, latest.get(MetricsTag.of(pipeline)),
                        points.get(MetricsTag.of(pipeline)), platform))
                .toList();
        return new ServiceEvidence(service.getId(), service.getName(), service.getDescription(),
                service.getScm().repositoryUrl(), service.getDeployment().artifactName(),
                service.getAppScan().applicationId(), service.getSonar().projectKey(),
                service.getNexusIq().application(), pipelineEvidence);
    }

    static PipelineEvidence pipeline(Pipeline pipeline, PipelineRun run, RunPoints points, PlatformSettings platform) {
        String jobUrl = pipeline.getSettings().jenkinsJobUrl(platform.jenkinsUrl());
        RunResult status = RunResult.of(pipeline, run);
        if (run == null) {
            return new PipelineEvidence(pipeline.getId(), pipeline.getType(), pipeline.isEnabled(), jobUrl, status, null);
        }
        String buildJobUrl = jobUrl != null ? jobUrl : PipelineSettings.jobUrl(run.job(), platform.jenkinsUrl());
        ServiceDefinition service = pipeline.getService();
        EvidenceLinks links = EvidenceLinks.of(buildJobUrl, run.build(), platform.asocUrl(),
                service.getAppScan().applicationId(), platform.sonarServerUrl(), service.getSonar().projectKey(),
                platform.nexusIqServerUrl());
        RunPoints recorded = points == null ? new RunPoints(List.of()) : points;
        String module = service.getName();
        BuildEvidence build = new BuildEvidence(run.build(), run.time(), run.result(), run.branch(), run.commit(),
                run.durationSeconds(), run.job(), links.buildUrl(), links.reportUrl(), links.testReportUrl(),
                links.artifactsUrl());
        RunEvidence evidence = new RunEvidence(build, recorded.coverage(module), recorded.testSuites(module),
                recorded.scans(module, links), recorded.releaseGate(), recorded.stages());
        return new PipelineEvidence(pipeline.getId(), pipeline.getType(), pipeline.isEnabled(), jobUrl, status, evidence);
    }
}
