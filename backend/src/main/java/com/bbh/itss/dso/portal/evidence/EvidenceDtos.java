package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.catalog.TestStage;
import com.bbh.itss.dso.portal.monitoring.RunResult;
import com.bbh.itss.dso.portal.pipeline.PipelineType;

import java.time.Instant;
import java.util.List;

/**
 * Responses of the change evidence API: for every pipeline of a product, what its latest run proved, in the
 * shape a ServiceNow change request asks for. Values the library did not record are null.
 */
public final class EvidenceDtos {

    private EvidenceDtos() {
    }

    public record ProductEvidence(Long productId, String code, String name, String description, String ownerTeam,
                                  String contactEmail, List<ServiceEvidence> services, String metricsError) {
    }

    /** A service with the identifiers its scans run under and its pipelines. */
    public record ServiceEvidence(Long serviceId, String name, String description, String repositoryUrl,
                                  String artifactName, String appScanApplicationId, String sonarProjectKey,
                                  String nexusIqApplication, List<PipelineEvidence> pipelines) {
    }

    /** {@code run} is null when no run of the pipeline was recorded. */
    public record PipelineEvidence(Long pipelineId, PipelineType type, boolean enabled, String jenkinsJobUrl,
                                   RunResult status, RunEvidence run) {
    }

    /** Everything the latest run of a pipeline recorded. */
    public record RunEvidence(BuildEvidence build, CoverageEvidence coverage, List<TestSuiteEvidence> testSuites,
                              List<ScanEvidence> scans, ReleaseGateEvidence releaseGate, List<StageEvidence> stages) {
    }

    /**
     * The Jenkins build: its links are known when the pipeline's Jenkins job is; {@code reportUrl} is the
     * pipeline report the library publishes, {@code testReportUrl} the build's JUnit results.
     */
    public record BuildEvidence(Long number, Instant finishedAt, RunResult result, String branch, String commit,
                                Long durationSeconds, String job, String url, String reportUrl, String testReportUrl,
                                String artifactsUrl) {
    }

    /** Line coverage of the unit tests against the required minimum. */
    public record CoverageEvidence(CheckStatus status, Double linePercent, Double requiredPercent, Long coveredLines,
                                   Long totalLines) {
    }

    /** The smoke, regression or performance test jobs a stage triggered. */
    public record TestSuiteEvidence(TestStage stage, CheckStatus status, Long jobs, Long passed, Long failed,
                                    Long notConfigured, Long durationMs) {
    }

    /** Findings by severity against the policy limits; Sonar reports its quality gate only. */
    public record ScanEvidence(EvidenceScanner scanner, CheckStatus status, Long critical, Long high, Long medium,
                               Long low, Long maxCritical, Long maxHigh, Long maxMedium, String link) {
    }

    public record ReleaseGateEvidence(boolean allowed, Long violations, String reason) {
    }

    public record StageEvidence(String name, CheckStatus status, Long durationSeconds, String reason) {
    }
}
