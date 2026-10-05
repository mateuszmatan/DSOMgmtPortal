package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;

public record PipelineRunResponse(
        Instant time,
        RunResult result,
        String branch,
        Long build,
        Long durationSeconds,
        String commit,
        String job,
        Long stagesTotal,
        Long passed,
        Long warned,
        Long failed,
        Long blocked,
        Long skipped) {

    static PipelineRunResponse from(PipelineRun run) {
        return DtoMapping.mapped(run, found -> new PipelineRunResponse(found.time(), found.result(), found.branch(),
                found.build(), found.durationSeconds(), found.commit(), found.job(), found.stagesTotal(),
                found.passed(), found.warned(), found.failed(), found.blocked(), found.skipped()));
    }
}
