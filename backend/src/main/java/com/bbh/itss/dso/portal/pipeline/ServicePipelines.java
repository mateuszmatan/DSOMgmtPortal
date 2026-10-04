package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.catalog.BuildTool;
import com.bbh.itss.dso.portal.catalog.DeployTarget;
import com.bbh.itss.dso.portal.catalog.ServiceDefinition;

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
