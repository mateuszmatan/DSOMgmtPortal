package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Service;

import java.util.List;

public record ServicePipelinesResponse(
        Long serviceId,
        String serviceName,
        String description,
        BuildTool buildTool,
        DeployTarget deployTarget,
        List<PipelineResponse> pipelines) {

    static ServicePipelinesResponse from(ServicePipelinesView view) {
        Service service = view.service();
        return new ServicePipelinesResponse(service.id(), service.name(), service.description(),
                service.settings().build().tool(), service.settings().deployment().target(),
                view.pipelines().stream().map(PipelineResponse::summary).toList());
    }
}
