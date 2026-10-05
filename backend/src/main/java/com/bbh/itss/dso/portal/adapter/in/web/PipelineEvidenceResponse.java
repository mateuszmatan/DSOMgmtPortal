package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.evidence.port.in.PipelineEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

public record PipelineEvidenceResponse(Long pipelineId, PipelineType type, boolean enabled, String jenkinsJobUrl,
                                       RunResult status, RunEvidenceResponse run) {

    static PipelineEvidenceResponse from(PipelineEvidence evidence) {
        Pipeline pipeline = evidence.pipeline();
        return new PipelineEvidenceResponse(pipeline.id(), pipeline.type(), pipeline.isEnabled(),
                evidence.jenkinsJobUrl(), evidence.status(), RunEvidenceResponse.from(evidence.run()));
    }
}
