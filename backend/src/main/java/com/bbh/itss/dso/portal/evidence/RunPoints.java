package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.catalog.TestStage;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.CoverageEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ReleaseGateEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ScanEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.StageEvidence;
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.TestSuiteEvidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class RunPoints {

    static final List<String> MEASUREMENTS = List.of("security_findings", "policy_status", "code_coverage",
            "test_execution", "release_gate", "vulnerabilities", "stage_event");

    private final List<Map<String, String>> rows;

    public RunPoints(List<Map<String, String>> rows) {
        this.rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public CoverageEvidence coverage(String module) {
        Optional<Map<String, String>> row = forModule("code_coverage", module);
        CheckStatus policy = policyStatus("coverage");
        if (row.isEmpty()) {
            return new CoverageEvidence(policy, null, null, null, null);
        }
        Map<String, String> coverage = row.get();
        boolean measured = !"no".equals(coverage.get("measured"));
        CheckStatus status = policy != CheckStatus.NO_DATA ? policy
                : !measured ? CheckStatus.SKIP
                : flag(coverage.get("met")) ? CheckStatus.PASS : CheckStatus.WARN;
        return new CoverageEvidence(status, measured ? decimal(coverage.get("line_pct")) : null,
                decimal(coverage.get("required")), measured ? number(coverage.get("covered")) : null,
                measured ? number(coverage.get("total")) : null);
    }

    public List<TestSuiteEvidence> testSuites(String module) {
        List<TestSuiteEvidence> suites = new ArrayList<>();
        for (TestStage stage : TestStage.values()) {
            Predicate<String> named = name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(stage.configKey());
            Optional<Map<String, String>> execution = forModule("test_execution", module, row -> named.test(row.get("suite")));
            CheckStatus stageStatus = rows("stage_event").stream().filter(row -> named.test(row.get("stage")))
                    .map(row -> CheckStatus.fromTag(row.get("status"))).findFirst().orElse(CheckStatus.NO_DATA);
            if (execution.isEmpty()) {
                suites.add(new TestSuiteEvidence(stage, stageStatus, null, null, null, null, null));
                continue;
            }
            Map<String, String> row = execution.get();
            Long failed = number(row.get("failed"));
            CheckStatus status = stageStatus != CheckStatus.NO_DATA ? stageStatus
                    : failed != null && failed > 0 ? CheckStatus.WARN : CheckStatus.PASS;
            suites.add(new TestSuiteEvidence(stage, status, number(row.get("total")), number(row.get("passed")), failed,
                    number(row.get("not_configured")), number(row.get("duration_ms"))));
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
        return rows("release_gate").stream().findFirst()
                .map(row -> new ReleaseGateEvidence("yes".equals(row.get("allowed")) || flag(row.get("allowed")),
                        number(row.get("violations")), text(row.get("reason"))))
                .orElse(null);
    }

    public List<StageEvidence> stages() {
        return rows("stage_event").stream()
                .sorted(Comparator.comparing((Map<String, String> row) -> number(row.get("order")),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(row -> new StageEvidence(row.get("stage"), CheckStatus.fromTag(row.get("status")),
                        number(row.get("duration_s")), text(row.get("reason"))))
                .toList();
    }

    private ScanEvidence appScan(EvidenceScanner scanner, String key, String module, String link) {
        Optional<Map<String, String>> row = findings(key, module);
        CheckStatus policy = policyStatus(key);
        if (row.isEmpty()) {
            return new ScanEvidence(scanner, policy, null, null, null, null, null, null, null, link);
        }
        Map<String, String> findings = row.get();
        CheckStatus status = policy != CheckStatus.NO_DATA ? policy : CheckStatus.fromTag(findings.get("status"));
        return new ScanEvidence(scanner, status, number(findings.get("critical")), number(findings.get("high")),
                number(findings.get("medium")), number(findings.get("low")), number(findings.get("max_critical")),
                number(findings.get("max_high")), number(findings.get("max_medium")), link);
    }

    private ScanEvidence counted(EvidenceScanner scanner, String policyKey, String vulnerabilityKey,
                                 Map<String, String> limits, String link) {
        Optional<Map<String, String>> counts = rows("vulnerabilities").stream()
                .filter(row -> vulnerabilityKey.equals(row.get("scanner"))).findFirst();
        Map<String, String> values = counts.orElse(Map.of());
        Map<String, String> limit = limits == null ? Map.of() : limits;
        return new ScanEvidence(scanner, policyStatus(policyKey), number(values.get("critical")),
                number(values.get("high")), number(values.get("medium")), number(values.get("low")),
                number(limit.get("max_critical")), number(limit.get("max_high")), number(limit.get("max_medium")), link);
    }

    private Optional<Map<String, String>> findings(String scanner, String module) {
        return forModule("security_findings", module, row -> scanner.equals(row.get("scanner")));
    }

    private CheckStatus policyStatus(String scanner) {
        return rows("policy_status").stream().filter(row -> scanner.equals(row.get("scanner")))
                .map(row -> CheckStatus.fromTag(row.get("status"))).findFirst().orElse(CheckStatus.NO_DATA);
    }

    private Optional<Map<String, String>> forModule(String measurement, String module) {
        return forModule(measurement, module, row -> true);
    }

    private Optional<Map<String, String>> forModule(String measurement, String module,
                                                    Predicate<Map<String, String>> filter) {
        List<Map<String, String>> candidates = rows(measurement).stream().filter(filter).toList();
        return candidates.stream().filter(row -> Objects.equals(module, row.get("module"))).findFirst()
                .or(() -> candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty());
    }

    private List<Map<String, String>> rows(String measurement) {
        return rows.stream().filter(row -> measurement.equals(row.get("_measurement"))).toList();
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
