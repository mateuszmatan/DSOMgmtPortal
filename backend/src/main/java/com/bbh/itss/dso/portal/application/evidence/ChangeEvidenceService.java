package com.bbh.itss.dso.portal.application.evidence;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.evidence.port.in.PipelineEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.ProductEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase;
import com.bbh.itss.dso.portal.application.evidence.port.in.ServiceEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.evidence.EvidenceLinks;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsUnavailableException;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class ChangeEvidenceService implements QueryEvidenceUseCase {

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final ManageGlobalSettingsUseCase settings;
    private final PipelineRunsPort runs;
    private final RunEvidencePort evidence;

    public ChangeEvidenceService(ProductRepositoryPort products, PipelineRepositoryPort pipelines,
                                 ManageGlobalSettingsUseCase settings, PipelineRunsPort runs,
                                 RunEvidencePort evidence) {
        this.products = products;
        this.pipelines = pipelines;
        this.settings = settings;
        this.runs = runs;
        this.evidence = evidence;
    }

    @Override
    @ReadOnly
    public ProductEvidence product(long productId) {
        Product product = products.load(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        PlatformSettings platform = settings.current().platform();
        List<Pipeline> productPipelines = pipelines.findByProductId(productId);
        Map<Long, List<Pipeline>> byService = productPipelines.stream()
                .collect(Collectors.groupingBy(pipeline -> pipeline.service().serviceId()));
        Readings readings = read(productPipelines.stream().map(pipeline -> tag(product, pipeline))
                .collect(Collectors.toSet()));
        List<ServiceEvidence> services = product.services().stream()
                .map(service -> service(service, byService.getOrDefault(service.id(), List.of()), readings, platform))
                .toList();
        return new ProductEvidence(product, services, readings.error());
    }

    private Readings read(Set<MetricsTag> tags) {
        try {
            Map<MetricsTag, PipelineRun> latest = runs.latestRuns(tags);
            return new Readings(latest, evidence.evidenceOf(latest), null);
        } catch (MetricsUnavailableException e) {
            return new Readings(Map.of(), Map.of(), e.getMessage());
        }
    }

    private static ServiceEvidence service(Service service, List<Pipeline> servicePipelines, Readings readings,
                                           PlatformSettings platform) {
        return new ServiceEvidence(service, servicePipelines.stream()
                .map(pipeline -> {
                    MetricsTag tag = MetricsTag.of(service, pipeline);
                    return pipeline(service, pipeline, readings.latest().get(tag), readings.evidence().get(tag),
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
        String buildJobUrl = jobUrl != null ? jobUrl : PipelineSettings.jobUrl(run.job(), platform.jenkinsUrl());
        ServiceSettings settings = service.settings();
        EvidenceLinks links = EvidenceLinks.of(buildJobUrl, run.build(), platform.asocUrl(),
                settings.appScan().applicationId(), platform.sonarServerUrl(), settings.sonar().projectKey(),
                platform.nexusIqServerUrl());
        RunEvidence points = recorded == null ? RunEvidence.none() : recorded;
        return new PipelineEvidence(pipeline, jobUrl, status, points.report(run, service.name(), links));
    }

    private static MetricsTag tag(Product product, Pipeline pipeline) {
        Service service = product.service(pipeline.service().serviceId()).orElseThrow(() -> new IllegalStateException(
                "pipeline " + pipeline.id() + " belongs to no service of product " + product.id()));
        return MetricsTag.of(service, pipeline);
    }

    private record Readings(Map<MetricsTag, PipelineRun> latest, Map<MetricsTag, RunEvidence> evidence,
                            String error) {
    }
}
