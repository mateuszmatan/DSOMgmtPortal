package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.evidence.port.in.PipelineEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.ProductEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase;
import com.bbh.itss.dso.portal.application.evidence.port.in.ServiceEvidence;
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidenceReport;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@RestController
@RequestMapping("/api/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final QueryEvidenceUseCase evidence;

    @GetMapping("/products/{id}")
    public ProductEvidenceResponse product(@PathVariable long id) {
        ProductEvidence found = evidence.product(id);
        Product product = found.product();
        return new ProductEvidenceResponse(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(),
                found.services().stream().map(ServiceEvidenceResponse::of).toList(), found.metricsError());
    }

    public record ProductEvidenceResponse(Long productId, String code, String name, String description,
                                          String ownerTeam, String contactEmail,
                                          List<ServiceEvidenceResponse> services, String metricsError) {
    }

    public record ServiceEvidenceResponse(Long serviceId, String name, String description, String repositoryUrl,
                                          String artifactName, String appScanApplicationId, String sonarProjectKey,
                                          String nexusIqApplication, List<PipelineEvidenceResponse> pipelines) {

        static ServiceEvidenceResponse of(ServiceEvidence evidence) {
            Service service = evidence.service();
            ServiceSettings settings = service.settings();
            return new ServiceEvidenceResponse(service.id(), service.name(), service.description(),
                    settings.scm().repositoryUrl(), settings.deployment().artifactName(),
                    settings.appScan().applicationId(), settings.sonar().projectKey(),
                    trimToNull(String.join(", ",
                            settings.nexusIqApplications().stream().map(NexusIqApplication::application).toList())),
                    evidence.pipelines().stream().map(PipelineEvidenceResponse::of).toList());
        }
    }

    public record PipelineEvidenceResponse(Long pipelineId, PipelineType type, boolean enabled, String jenkinsJobUrl,
                                           RunResult status, RunEvidenceReport run) {

        static PipelineEvidenceResponse of(PipelineEvidence evidence) {
            Pipeline pipeline = evidence.pipeline();
            return new PipelineEvidenceResponse(pipeline.id(), pipeline.type(), pipeline.isEnabled(),
                    evidence.jenkinsJobUrl(), evidence.status(), evidence.run());
        }
    }
}
