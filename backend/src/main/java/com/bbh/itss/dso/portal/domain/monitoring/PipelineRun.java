package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;

import java.time.Instant;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.trim;

public record PipelineRun(Instant time, RunResult result, String branch, Long build, Long durationSeconds,
                          String commit, String job, Long stagesTotal, Long passed, Long warned, Long failed,
                          Long blocked, Long skipped) {

    public PipelineRun {
        Objects.requireNonNull(time, "a run has the time it finished");
    }

    public String buildUrl(String jenkinsUrl, String configuredJob) {
        if (build == null) {
            return null;
        }
        String recorded = PipelineSettings.jobUrl(job, jenkinsUrl);
        String jobUrl = recorded == null ? PipelineSettings.jobUrl(configuredJob, jenkinsUrl) : recorded;
        return jobUrl == null ? null : stripEnd(trim(jobUrl), "/") + "/" + build + "/";
    }
}
