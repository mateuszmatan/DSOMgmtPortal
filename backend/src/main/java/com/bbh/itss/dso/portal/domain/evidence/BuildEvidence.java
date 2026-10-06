package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;

public record BuildEvidence(Long number, Instant finishedAt, RunResult result, String branch, String commit,
                            String artifactVersion, Long durationSeconds, String job, String url, String reportUrl,
                            String testReportUrl, String artifactsUrl, Instant configRenderedAt,
                            String configSha256) {
}
