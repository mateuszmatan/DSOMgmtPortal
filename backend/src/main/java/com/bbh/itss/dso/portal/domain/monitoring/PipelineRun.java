package com.bbh.itss.dso.portal.domain.monitoring;

import java.time.Instant;
import java.util.Objects;

public record PipelineRun(
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

    public PipelineRun {
        Objects.requireNonNull(time, "a run has the time it finished");
    }
}
