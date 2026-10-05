package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineHealth;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

public record PipelineHealthResponse(PipelineResponse pipeline, RunResult status, PipelineRunResponse lastRun) {

    static PipelineHealthResponse from(PipelineHealth health) {
        return new PipelineHealthResponse(PipelineResponse.monitored(health.pipeline()), health.status(),
                PipelineRunResponse.from(health.lastRun(), health.pipeline()));
    }
}
