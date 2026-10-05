package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.evidence.port.in.ServiceEvidence;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;

import java.util.List;

public record ServiceEvidenceResponse(Long serviceId, String name, String description, String repositoryUrl,
                                      String artifactName, String appScanApplicationId, String sonarProjectKey,
                                      String nexusIqApplication, List<PipelineEvidenceResponse> pipelines) {

    static ServiceEvidenceResponse from(ServiceEvidence evidence) {
        Service service = evidence.service();
        ServiceSettings settings = service.settings();
        return new ServiceEvidenceResponse(service.id(), service.name(), service.description(),
                settings.scm().repositoryUrl(), settings.deployment().artifactName(),
                settings.appScan().applicationId(), settings.sonar().projectKey(), settings.nexusIq().application(),
                evidence.pipelines().stream().map(PipelineEvidenceResponse::from).toList());
    }
}
