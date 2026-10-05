package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.evidence.BuildEvidence;
import com.bbh.itss.dso.portal.domain.evidence.CheckStatus;
import com.bbh.itss.dso.portal.domain.evidence.CoverageEvidence;
import com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner;
import com.bbh.itss.dso.portal.domain.evidence.ReleaseGateEvidence;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidenceReport;
import com.bbh.itss.dso.portal.domain.evidence.ScanEvidence;
import com.bbh.itss.dso.portal.domain.evidence.StageEvidence;
import com.bbh.itss.dso.portal.domain.evidence.TestSuiteEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;
import java.util.List;

public record RunEvidenceResponse(Build build, Coverage coverage, List<TestSuite> testSuites, List<Scan> scans,
                                  ReleaseGate releaseGate, List<Stage> stages) {

    static RunEvidenceResponse from(RunEvidenceReport report) {
        return DtoMapping.mapped(report, found -> new RunEvidenceResponse(Build.from(found.build()),
                Coverage.from(found.coverage()), found.testSuites().stream().map(TestSuite::from).toList(),
                found.scans().stream().map(Scan::from).toList(), ReleaseGate.from(found.releaseGate()),
                found.stages().stream().map(Stage::from).toList()));
    }

    public record Build(Long number, Instant finishedAt, RunResult result, String branch, String commit,
                        Long durationSeconds, String job, String url, String reportUrl, String testReportUrl,
                        String artifactsUrl) {

        static Build from(BuildEvidence build) {
            return new Build(build.number(), build.finishedAt(), build.result(), build.branch(), build.commit(),
                    build.durationSeconds(), build.job(), build.url(), build.reportUrl(), build.testReportUrl(),
                    build.artifactsUrl());
        }
    }

    public record Coverage(CheckStatus status, Double linePercent, Double requiredPercent, Long coveredLines,
                           Long totalLines) {

        static Coverage from(CoverageEvidence coverage) {
            return new Coverage(coverage.status(), coverage.linePercent(), coverage.requiredPercent(),
                    coverage.coveredLines(), coverage.totalLines());
        }
    }

    public record TestSuite(TestStage stage, CheckStatus status, Long jobs, Long passed, Long failed,
                            Long notConfigured, Long durationMs) {

        static TestSuite from(TestSuiteEvidence suite) {
            return new TestSuite(suite.stage(), suite.status(), suite.jobs(), suite.passed(), suite.failed(),
                    suite.notConfigured(), suite.durationMs());
        }
    }

    public record Scan(EvidenceScanner scanner, CheckStatus status, Long critical, Long high, Long medium, Long low,
                       Long maxCritical, Long maxHigh, Long maxMedium, String link) {

        static Scan from(ScanEvidence scan) {
            return new Scan(scan.scanner(), scan.status(), scan.critical(), scan.high(), scan.medium(), scan.low(),
                    scan.maxCritical(), scan.maxHigh(), scan.maxMedium(), scan.link());
        }
    }

    public record ReleaseGate(boolean allowed, Long violations, String reason) {

        static ReleaseGate from(ReleaseGateEvidence gate) {
            return DtoMapping.mapped(gate, found -> new ReleaseGate(found.allowed(), found.violations(),
                    found.reason()));
        }
    }

    public record Stage(String name, CheckStatus status, Long durationSeconds, String reason) {

        static Stage from(StageEvidence stage) {
            return new Stage(stage.name(), stage.status(), stage.durationSeconds(), stage.reason());
        }
    }
}
