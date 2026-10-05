package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class RunEvidence {

    public static final List<String> MEASUREMENTS = List.of("security_findings", "policy_status", "code_coverage",
            "test_execution", "release_gate", "vulnerabilities", "stage_event");

    private final List<EvidencePoint> points;

    public RunEvidence(List<EvidencePoint> points) {
        this.points = List.copyOf(points);
    }

    public static RunEvidence none() {
        return new RunEvidence(List.of());
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public RunEvidenceReport report(PipelineRun run, String module, EvidenceLinks links) {
        return new RunEvidenceReport(BuildEvidence.of(run, links), coverage(module), testSuites(module),
                scans(module, links), releaseGate(), stages());
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
                : flag(coverage.value("met")) ? CheckStatus.PASS : CheckStatus.WARN;
        return new CoverageEvidence(status, measured ? decimal(coverage.value("line_pct")) : null,
                decimal(coverage.value("required")), measured ? number(coverage.value("covered")) : null,
                measured ? number(coverage.value("total")) : null);
    }

    public List<TestSuiteEvidence> testSuites(String module) {
        List<TestSuiteEvidence> suites = new ArrayList<>();
        for (TestStage stage : TestStage.values()) {
            Predicate<String> named = name -> name != null
                    && name.toLowerCase(Locale.ROOT).startsWith(stage.configKey());
            Optional<EvidencePoint> execution = forModule("test_execution", module,
                    point -> named.test(point.value("suite")));
            CheckStatus stageStatus = points("stage_event").stream().filter(point -> named.test(point.value("stage")))
                    .map(point -> CheckStatus.fromTag(point.value("status"))).findFirst().orElse(CheckStatus.NO_DATA);
            if (execution.isEmpty()) {
                suites.add(new TestSuiteEvidence(stage, stageStatus, null, null, null, null, null));
                continue;
            }
            EvidencePoint point = execution.get();
            Long failed = number(point.value("failed"));
            CheckStatus status = stageStatus != CheckStatus.NO_DATA ? stageStatus
                    : failed != null && failed > 0 ? CheckStatus.WARN : CheckStatus.PASS;
            suites.add(new TestSuiteEvidence(stage, status, number(point.value("total")), number(point.value("passed")),
                    failed, number(point.value("not_configured")), number(point.value("duration_ms"))));
        }
        return suites;
    }

    public List<ScanEvidence> scans(String module, EvidenceLinks links) {
        return List.of(
                appScan(EvidenceScanner.SAST, "sast", module, links.appScanUrl()),
                appScan(EvidenceScanner.DAST, "dast", module, links.appScanUrl()),
                counted(EvidenceScanner.SONARQUBE, "sonar", "sonar", null, links.sonarUrl()),
                counted(EvidenceScanner.NEXUS_IQ, "iast", "nexusiq", findings("niq", module).orElse(null),
                        links.nexusIqUrl()));
    }

    public ReleaseGateEvidence releaseGate() {
        return points("release_gate").stream().findFirst()
                .map(point -> new ReleaseGateEvidence(
                        "yes".equals(point.value("allowed")) || flag(point.value("allowed")),
                        number(point.value("violations")), text(point.value("reason"))))
                .orElse(null);
    }

    public List<StageEvidence> stages() {
        return points("stage_event").stream()
                .sorted(Comparator.comparing((EvidencePoint point) -> number(point.value("order")),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(point -> new StageEvidence(point.value("stage"), CheckStatus.fromTag(point.value("status")),
                        number(point.value("duration_s")), text(point.value("reason"))))
                .toList();
    }

    private ScanEvidence appScan(EvidenceScanner scanner, String key, String module, String link) {
        Optional<EvidencePoint> point = findings(key, module);
        CheckStatus policy = policyStatus(key);
        if (point.isEmpty()) {
            return new ScanEvidence(scanner, policy, null, null, null, null, null, null, null, link);
        }
        EvidencePoint findings = point.get();
        CheckStatus status = policy != CheckStatus.NO_DATA ? policy : CheckStatus.fromTag(findings.value("status"));
        return new ScanEvidence(scanner, status, number(findings.value("critical")), number(findings.value("high")),
                number(findings.value("medium")), number(findings.value("low")), number(findings.value("max_critical")),
                number(findings.value("max_high")), number(findings.value("max_medium")), link);
    }

    private ScanEvidence counted(EvidenceScanner scanner, String policyKey, String vulnerabilityKey,
                                 EvidencePoint limits, String link) {
        Optional<EvidencePoint> counts = points("vulnerabilities").stream()
                .filter(point -> vulnerabilityKey.equals(point.value("scanner"))).findFirst();
        return new ScanEvidence(scanner, policyStatus(policyKey), count(counts, "critical"), count(counts, "high"),
                count(counts, "medium"), count(counts, "low"), limit(limits, "max_critical"), limit(limits, "max_high"),
                limit(limits, "max_medium"), link);
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
                .or(() -> candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty());
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

    static Long number(String value) {
        Double decimal = decimal(value);
        return decimal == null ? null : (long) Math.floor(decimal);
    }

    static Double decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean flag(String value) {
        Long number = number(value);
        return number != null && number > 0;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
