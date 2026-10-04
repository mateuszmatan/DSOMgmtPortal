package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.catalog.TestStage;
import com.bbh.itss.dso.portal.monitoring.RunResult;
import com.bbh.itss.dso.portal.pipeline.PipelineType;

import java.time.Instant;
import java.util.List;

public final class EvidenceDtos {

    private EvidenceDtos() {
    }

    public record ProductEvidence(Long productId, String code, String name, String description, String ownerTeam,
                                  String contactEmail, List<ServiceEvidence> services, String metricsError) {
    }

    public record ServiceEvidence(Long serviceId, String name, String description, String repositoryUrl,
                                  String artifactName, String appScanApplicationId, String sonarProjectKey,
                                  String nexusIqApplication, List<PipelineEvidence> pipelines) {
    }

    public record PipelineEvidence(Long pipelineId, PipelineType type, boolean enabled, String jenkinsJobUrl,
                                   RunResult status, RunEvidence run) {
    }

    public record RunEvidence(BuildEvidence build, CoverageEvidence coverage, List<TestSuiteEvidence> testSuites,
                              List<ScanEvidence> scans, ReleaseGateEvidence releaseGate, List<StageEvidence> stages) {
    }

    public record BuildEvidence(Long number, Instant finishedAt, RunResult result, String branch, String commit,
                                Long durationSeconds, String job, String url, String reportUrl, String testReportUrl,
                                String artifactsUrl) {
    }

    public record CoverageEvidence(CheckStatus status, Double linePercent, Double requiredPercent, Long coveredLines,
                                   Long totalLines) {
    }

    public record TestSuiteEvidence(TestStage stage, CheckStatus status, Long jobs, Long passed, Long failed,
                                    Long notConfigured, Long durationMs) {
    }

    public record ScanEvidence(EvidenceScanner scanner, CheckStatus status, Long critical, Long high, Long medium,
                               Long low, Long maxCritical, Long maxHigh, Long maxMedium, String link) {
    }

    public record ReleaseGateEvidence(boolean allowed, Long violations, String reason) {
    }

    public record StageEvidence(String name, CheckStatus status, Long durationSeconds, String reason) {
    }
}
