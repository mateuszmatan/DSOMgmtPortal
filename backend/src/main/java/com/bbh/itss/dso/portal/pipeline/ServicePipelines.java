package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.catalog.ServiceDefinition;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;

import java.util.List;

public record ServicePipelines(
        Long serviceId,
        String serviceName,
        String description,
        BuildTool buildTool,
        DeployTarget deployTarget,
        List<PipelineResponse> pipelines) {

    static ServicePipelines of(ServiceDefinition service, List<PipelineResponse> pipelines) {
        return new ServicePipelines(service.getId(), service.getName(), service.getDescription(),
                service.getBuild().tool(), service.getDeployment().target(), pipelines);
    }
}
