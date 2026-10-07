package com.bbh.itss.dso.portal.application.evidence.port.in;

import com.bbh.itss.dso.portal.domain.evidence.RunEvidenceReport;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

import static java.util.Objects.requireNonNull;

public record PipelineEvidence(Pipeline pipeline, String jenkinsJobUrl, RunResult status, RunEvidenceReport run) {

    public PipelineEvidence {
        requireNonNull(pipeline, "evidence belongs to a pipeline");
    }
}
