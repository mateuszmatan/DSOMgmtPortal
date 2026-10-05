package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;

public record BuildEvidence(Long number, Instant finishedAt, RunResult result, String branch, String commit,
                            Long durationSeconds, String job, String url, String reportUrl, String testReportUrl,
                            String artifactsUrl) {

    public static BuildEvidence of(PipelineRun run, EvidenceLinks links) {
        return new BuildEvidence(run.build(), run.time(), run.result(), run.branch(), run.commit(),
                run.durationSeconds(), run.job(), links.buildUrl(), links.reportUrl(), links.testReportUrl(),
                links.artifactsUrl());
    }
}
