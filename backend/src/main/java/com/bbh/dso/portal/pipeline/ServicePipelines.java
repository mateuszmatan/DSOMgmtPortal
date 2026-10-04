package com.bbh.dso.portal.pipeline;

import com.bbh.dso.portal.catalog.BuildTool;
import com.bbh.dso.portal.catalog.DeployTarget;

import java.util.List;

public record ServicePipelines(
        Long serviceId,
        String serviceName,
        String description,
        BuildTool buildTool,
        DeployTarget deployTarget,
        List<PipelineResponse> pipelines) {
}
