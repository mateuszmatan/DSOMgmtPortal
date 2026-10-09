package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.FAIL;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.NO_DATA;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.PASS;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.SKIP;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.WARN;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.fromTag;
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.DAST;
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.NEXUS_IQ;
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.SAST;
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.SONARQUBE;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.decimal;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.number;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.positive;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.Strings.CS;

public final class RunEvidence {

    public static final List<String> MEASUREMENTS = List.of("security_findings", "policy_status", "code_coverage",
            "test_execution", "release_gate", "vulnerabilities", "stage_event", "build_evidence", "goldenfix");
    private static final Duration TOLERANCE = ofSeconds(2);

    private final List<EvidencePoint> points;

    public RunEvidence(List<EvidencePoint> points) {
        this.points = List.copyOf(points);
    }

    public static RunEvidence none() {
        return new RunEvidence(List.of());
    }

    public static Instant windowStart(PipelineRun run) {
        long seconds = getIfNull(run.durationSeconds(), 0L);
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
                scans(module, links), releaseGate(), stages(), goldenFix(module));
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
            return CoverageEvidence.builder().status(policy).build();
        }
        EvidencePoint coverage = point.get();
        boolean measured = !"no".equals(coverage.value("measured"));
        CheckStatus status = policy != NO_DATA ? policy
                : !measured ? SKIP
                : positive(coverage.value("met")) ? PASS : WARN;
        return new CoverageEvidence(status, measured ? decimal(coverage.value("line_pct")) : null,
                decimal(coverage.value("required")), measured ? number(coverage.value("covered")) : null,
                measured ? number(coverage.value("total")) : null);
    }

    public List<TestSuiteEvidence> testSuites(String module) {
        List<TestSuiteEvidence> suites = new ArrayList<>();
        for (TestSuite suite : TestSuite.values()) {
            Predicate<String> named = name -> CS.startsWith(lowerCase(name, ROOT), suite.tag());
            Optional<EvidencePoint> execution = forModule("test_execution", module,
                    point -> named.test(point.value("suite")));
            CheckStatus stageStatus = points("stage_event").stream().filter(point -> named.test(point.value("stage")))
                    .map(point -> fromTag(point.value("status"))).findFirst().orElse(NO_DATA);
            if (execution.isEmpty()) {
                suites.add(TestSuiteEvidence.builder().suite(suite).status(stageStatus).build());
                continue;
            }
            EvidencePoint point = execution.get();
            Long failed = number(point.value("failed"));
            CheckStatus status = stageStatus != NO_DATA ? stageStatus
                    : failed != null && failed > 0 ? WARN : PASS;
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
                appScan(SAST, "sast", module, link(recorded, "sast", links.appScanUrl())),
                appScan(DAST, "dast", module, link(recorded, "dast", links.appScanUrl())),
                counted(SONARQUBE, sonar != NO_DATA ? sonar : gateStatus(qualityGate),
                        "sonar", null, "NONE".equals(qualityGate) ? null : qualityGate,
                        link(recorded, "sonar", links.sonarUrl())),
                counted(NEXUS_IQ, policyStatus("iast"), "nexusiq",
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
                .sorted(comparing((EvidencePoint point) -> number(point.value("order")), nullsLast(naturalOrder())))
                .map(point -> new StageEvidence(point.value("stage"), fromTag(point.value("status")),
                        number(point.value("duration_s")), trimToNull(point.value("reason"))))
                .toList();
    }

    public GoldenFixEvidence goldenFix(String module) {
        return forModule("goldenfix", module)
                .map(point -> new GoldenFixEvidence(trimToNull(point.value("status")), whole(point.value("offered")),
                        whole(point.value("applied")), whole(point.value("unresolved")),
                        positive(point.value("pr_raised")), trimToNull(point.value("pr_url")),
                        trimToNull(point.value("pr_title"))))
                .orElse(null);
    }

    private ScanEvidence appScan(EvidenceScanner scanner, String key, String module, String link) {
        Optional<EvidencePoint> point = findings(key, module);
        CheckStatus policy = policyStatus(key);
        if (point.isEmpty()) {
            return ScanEvidence.builder().scanner(scanner).status(policy).link(link).build();
        }
        EvidencePoint findings = point.get();
        CheckStatus status = policy != NO_DATA ? policy : fromTag(findings.value("status"));
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
        return defaultIfBlank(trim(recorded.value(report + "_report_url")), fallback);
    }

    private static CheckStatus gateStatus(String qualityGate) {
        return switch (String.valueOf(qualityGate)) {
            case "OK" -> PASS;
            case "WARN" -> WARN;
            case "ERROR" -> FAIL;
            default -> NO_DATA;
        };
    }

    private Optional<EvidencePoint> findings(String scanner, String module) {
        return forModule("security_findings", module, point -> scanner.equals(point.value("scanner")));
    }

    private CheckStatus policyStatus(String scanner) {
        return points("policy_status").stream().filter(point -> scanner.equals(point.value("scanner")))
                .map(point -> fromTag(point.value("status"))).findFirst().orElse(NO_DATA);
    }

    private Optional<EvidencePoint> forModule(String measurement, String module) {
        return forModule(measurement, module, point -> true);
    }

    private Optional<EvidencePoint> forModule(String measurement, String module, Predicate<EvidencePoint> filter) {
        List<EvidencePoint> candidates = points(measurement).stream().filter(filter).toList();
        return candidates.stream().filter(point -> Objects.equals(module, point.value("module"))).findFirst()
                .or(() -> candidates.size() == 1 && isBlank(candidates.get(0).value("module"))
                        ? Optional.of(candidates.get(0)) : Optional.empty());
    }

    private List<EvidencePoint> points(String measurement) {
        return points.stream().filter(point -> point.isOf(measurement)).toList();
    }

    private static Long count(Optional<EvidencePoint> point, String name) {
        return point.map(found -> number(found.value(name))).orElse(null);
    }

    private static int whole(String value) {
        return getIfNull(number(value), 0L).intValue();
    }

    private static Long limit(EvidencePoint point, String name) {
        return point == null ? null : number(point.value(name));
    }

    private static Instant instant(String value) {
        try {
            return isBlank(value) ? null : Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
