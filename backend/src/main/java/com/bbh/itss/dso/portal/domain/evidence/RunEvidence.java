package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.decimal;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.number;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.positive;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimToNull;

public final class RunEvidence {

    public static final List<String> MEASUREMENTS = List.of("security_findings", "policy_status", "code_coverage",
            "test_execution", "release_gate", "vulnerabilities", "stage_event", "build_evidence");
    private static final Duration TOLERANCE = Duration.ofSeconds(2);

    private final List<EvidencePoint> points;

    public RunEvidence(List<EvidencePoint> points) {
        this.points = List.copyOf(points);
    }

    public static RunEvidence none() {
        return new RunEvidence(List.of());
    }

    public static Instant windowStart(PipelineRun run) {
        long seconds = run.durationSeconds() == null ? 0 : run.durationSeconds();
        return run.time().minusSeconds(seconds).minus(TOLERANCE).minus(TOLERANCE);
    }

    public static Instant windowEnd(PipelineRun run) {
        return run.time().plus(TOLERANCE);
    }

    public static boolean recordedDuring(PipelineRun run, String measurement, Instant at) {
        Instant earliest = "stage_event".equals(measurement) ? windowStart(run) : run.time().minus(TOLERANCE);
        return !at.isBefore(earliest) && !at.isAfter(windowEnd(run));
    }

    public RunEvidenceReport report(PipelineRun run, String module, EvidenceLinks links) {
        return new RunEvidenceReport(build(run, module, links), coverage(module), testSuites(module),
                scans(module, links), releaseGate(), stages());
    }

    public BuildEvidence build(PipelineRun run, String module, EvidenceLinks links) {
        EvidencePoint recorded = recorded(module);
        return new BuildEvidence(run.build(), run.time(), run.result(), run.branch(), run.commit(),
                trimToNull(recorded.value("artifact_version")), run.durationSeconds(), run.job(), links.buildUrl(),
                links.reportUrl(), links.testReportUrl(), links.artifactsUrl(),
                instant(recorded.value("config_rendered_at")), trimToNull(recorded.value("config_sha256")));
    }

    public CoverageEvidence coverage(String module) {
        Optional<EvidencePoint> point = forModule("code_coverage", module);
        CheckStatus policy = policyStatus("coverage");
        if (point.isEmpty()) {
            return new CoverageEvidence(policy, null, null, null, null);
        }
        EvidencePoint coverage = point.get();
        boolean measured = !"no".equals(coverage.value("measured"));
        CheckStatus status = policy != CheckStatus.NO_DATA ? policy
                : !measured ? CheckStatus.SKIP
                : positive(coverage.value("met")) ? CheckStatus.PASS : CheckStatus.WARN;
        return new CoverageEvidence(status, measured ? decimal(coverage.value("line_pct")) : null,
                decimal(coverage.value("required")), measured ? number(coverage.value("covered")) : null,
                measured ? number(coverage.value("total")) : null);
    }

    public List<TestSuiteEvidence> testSuites(String module) {
        List<TestSuiteEvidence> suites = new ArrayList<>();
        for (TestSuite suite : TestSuite.values()) {
            Predicate<String> named = name -> name != null
                    && name.toLowerCase(Locale.ROOT).startsWith(suite.tag());
            Optional<EvidencePoint> execution = forModule("test_execution", module,
                    point -> named.test(point.value("suite")));
            CheckStatus stageStatus = points("stage_event").stream().filter(point -> named.test(point.value("stage")))
                    .map(point -> CheckStatus.fromTag(point.value("status"))).findFirst().orElse(CheckStatus.NO_DATA);
            if (execution.isEmpty()) {
                suites.add(new TestSuiteEvidence(suite, stageStatus, null, null, null, null, null, null));
                continue;
            }
            EvidencePoint point = execution.get();
            Long failed = number(point.value("failed"));
            CheckStatus status = stageStatus != CheckStatus.NO_DATA ? stageStatus
                    : failed != null && failed > 0 ? CheckStatus.WARN : CheckStatus.PASS;
            suites.add(new TestSuiteEvidence(suite, status, number(point.value("total")), number(point.value("passed")),
                    failed, number(point.value("skipped")), number(point.value("not_configured")),
                    number(point.value("duration_ms"))));
        }
        return suites;
    }

    public List<ScanEvidence> scans(String module, EvidenceLinks links) {
        EvidencePoint recorded = recorded(module);
        String qualityGate = trimToNull(recorded.value("sonar_quality_gate"));
        CheckStatus sonar = policyStatus("sonar");
        return List.of(
                appScan(EvidenceScanner.SAST, "sast", module, link(recorded, "sast", links.appScanUrl())),
                appScan(EvidenceScanner.DAST, "dast", module, link(recorded, "dast", links.appScanUrl())),
                counted(EvidenceScanner.SONARQUBE, sonar != CheckStatus.NO_DATA ? sonar : gateStatus(qualityGate),
                        "sonar", null, "NONE".equals(qualityGate) ? null : qualityGate,
                        link(recorded, "sonar", links.sonarUrl())),
                counted(EvidenceScanner.NEXUS_IQ, policyStatus("iast"), "nexusiq",
                        findings("niq", module).orElse(null), null, link(recorded, "nexusiq", links.nexusIqUrl())));
    }

    public ReleaseGateEvidence releaseGate() {
        return points("release_gate").stream().findFirst()
                .map(point -> new ReleaseGateEvidence(
                        "yes".equals(point.value("allowed")) || positive(point.value("allowed")),
                        number(point.value("violations")), trimToNull(point.value("reason"))))
                .orElse(null);
    }

    public List<StageEvidence> stages() {
        return points("stage_event").stream()
                .sorted(Comparator.comparing((EvidencePoint point) -> number(point.value("order")),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(point -> new StageEvidence(point.value("stage"), CheckStatus.fromTag(point.value("status")),
                        number(point.value("duration_s")), trimToNull(point.value("reason"))))
                .toList();
    }

    private ScanEvidence appScan(EvidenceScanner scanner, String key, String module, String link) {
        Optional<EvidencePoint> point = findings(key, module);
        CheckStatus policy = policyStatus(key);
        if (point.isEmpty()) {
            return new ScanEvidence(scanner, policy, null, null, null, null, null, null, null, null, link);
        }
        EvidencePoint findings = point.get();
        CheckStatus status = policy != CheckStatus.NO_DATA ? policy : CheckStatus.fromTag(findings.value("status"));
        return new ScanEvidence(scanner, status, number(findings.value("critical")), number(findings.value("high")),
                number(findings.value("medium")), number(findings.value("low")), number(findings.value("max_critical")),
                number(findings.value("max_high")), number(findings.value("max_medium")), null, link);
    }

    private ScanEvidence counted(EvidenceScanner scanner, CheckStatus status, String vulnerabilityKey,
                                 EvidencePoint limits, String qualityGate, String link) {
        Optional<EvidencePoint> counts = points("vulnerabilities").stream()
                .filter(point -> vulnerabilityKey.equals(point.value("scanner"))).findFirst();
        return new ScanEvidence(scanner, status, count(counts, "critical"), count(counts, "high"),
                count(counts, "medium"), count(counts, "low"), limit(limits, "max_critical"), limit(limits, "max_high"),
                limit(limits, "max_medium"), qualityGate, link);
    }

    private EvidencePoint recorded(String module) {
        return forModule("build_evidence", module).orElseGet(() -> new EvidencePoint("build_evidence", Map.of()));
    }

    private static String link(EvidencePoint recorded, String report, String fallback) {
        return Text.orDefault(recorded.value(report + "_report_url"), fallback);
    }

    private static CheckStatus gateStatus(String qualityGate) {
        return switch (String.valueOf(qualityGate)) {
            case "OK" -> CheckStatus.PASS;
            case "WARN" -> CheckStatus.WARN;
            case "ERROR" -> CheckStatus.FAIL;
            default -> CheckStatus.NO_DATA;
        };
    }

    private Optional<EvidencePoint> findings(String scanner, String module) {
        return forModule("security_findings", module, point -> scanner.equals(point.value("scanner")));
    }

    private CheckStatus policyStatus(String scanner) {
        return points("policy_status").stream().filter(point -> scanner.equals(point.value("scanner")))
                .map(point -> CheckStatus.fromTag(point.value("status"))).findFirst().orElse(CheckStatus.NO_DATA);
    }

    private Optional<EvidencePoint> forModule(String measurement, String module) {
        return forModule(measurement, module, point -> true);
    }

    private Optional<EvidencePoint> forModule(String measurement, String module, Predicate<EvidencePoint> filter) {
        List<EvidencePoint> candidates = points(measurement).stream().filter(filter).toList();
        return candidates.stream().filter(point -> Objects.equals(module, point.value("module"))).findFirst()
                .or(() -> candidates.size() == 1 && Text.isBlank(candidates.getFirst().value("module"))
                        ? Optional.of(candidates.getFirst()) : Optional.empty());
    }

    private List<EvidencePoint> points(String measurement) {
        return points.stream().filter(point -> point.isOf(measurement)).toList();
    }

    private static Long count(Optional<EvidencePoint> point, String name) {
        return point.map(found -> number(found.value(name))).orElse(null);
    }

    private static Long limit(EvidencePoint point, String name) {
        return point == null ? null : number(point.value(name));
    }

    private static Instant instant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
