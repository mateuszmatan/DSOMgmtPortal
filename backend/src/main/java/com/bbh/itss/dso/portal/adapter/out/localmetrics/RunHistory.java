package com.bbh.itss.dso.portal.adapter.out.localmetrics;

import com.bbh.itss.dso.portal.domain.evidence.CheckStatus;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.FAIL;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.PASS;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.SKIP;
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.WARN;
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.ABORTED;
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.FAILURE;
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.SUCCESS;
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.UNSTABLE;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.round;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Locale.ROOT;
import static java.util.Map.entry;

final class RunHistory {

    private static final List<String> FEATURES = List.of("audit-trail", "pdf-export", "sso-login", "retry-policy",
            "new-dashboard", "bulk-upload");
    private static final Map<String, String> TEST_SUITES = Map.of("Unit Tests", "unit", "Smoke Tests", "smoke",
            "Regression Tests", "regression", "Performance Tests", "performance");
    private static final Map<String, Double> EFFORT = Map.ofEntries(entry("Checkout", 0.15),
            entry("Build", 1.0), entry("Unit Tests", 1.0), entry("SonarQube", 0.6),
            entry("AppScan SAST", 1.5), entry("Nexus IQ", 0.4), entry("Publish Artifact", 0.3),
            entry("Read Security Run", 0.1), entry("Deploy RD", 0.8), entry("Smoke Tests", 0.5),
            entry("Regression Tests", 1.5), entry("Performance Tests", 1.2), entry("AppScan DAST", 1.8),
            entry("Deploy QC", 0.8), entry("Release Gate", 0.1));
    private static final Map<String, String> REASONS = Map.ofEntries(
            entry("Build", "Compilation failed in module core"),
            entry("Unit Tests", "Unit tests failed"),
            entry("SonarQube", "Quality gate: coverage on new code below 70%"),
            entry("AppScan SAST", "High findings above the limit"),
            entry("AppScan DAST", "DAST reported a high finding"),
            entry("Nexus IQ", "Policy violation: a component with a critical CVE"),
            entry("Publish Artifact", "Nexus answered 502 Bad Gateway"),
            entry("Read Security Run", "No security run recorded for this commit"),
            entry("Deploy RD", "Rollout did not become ready within 10 minutes"),
            entry("Deploy QC", "Rollout did not become ready within 10 minutes"),
            entry("Smoke Tests", "Login smoke test failed"),
            entry("Regression Tests", "Regression tests failed"),
            entry("Performance Tests", "p95 latency above the limit"),
            entry("Release Gate", "The release gate refused the build"));
    private static final Map<PipelineType, Profile> PROFILES = Map.of(
            FULL, new Profile(1.4, 45, 95, List.of("Checkout", "Build", "Unit Tests", "SonarQube",
                    "AppScan SAST", "Nexus IQ", "Publish Artifact", "Deploy RD", "Smoke Tests", "Regression Tests",
                    "Deploy QC", "Release Gate")),
            SECURITY, new Profile(0.7, 20, 45, List.of("Checkout", "Build", "Unit Tests",
                    "AppScan SAST", "Nexus IQ", "SonarQube", "AppScan DAST", "Release Gate")),
            EXTENDED, new Profile(0.4, 70, 130, List.of("Checkout", "Read Security Run", "Deploy RD",
                    "Smoke Tests", "Regression Tests", "Performance Tests", "AppScan DAST", "Deploy QC",
                    "Release Gate")),
            SAST, new Profile(0.9, 6, 18, List.of("Checkout", "Build", "AppScan SAST", "Release Gate")));

    private final MetricsTag tag;
    private final String job;
    private final List<String> modules;
    private final Profile profile;
    private final Random random;
    private final double health;
    private final long leadTimeSeconds;
    private final String release;

    RunHistory(MetricsTag tag, String job, PipelineType type, List<String> modules, Random random) {
        this.tag = tag;
        this.job = job;
        this.modules = List.copyOf(modules);
        this.profile = PROFILES.get(type);
        this.random = random;
        this.health = 0.75 + random.nextDouble() * 0.22;
        this.leadTimeSeconds = 3_600L * (2 + random.nextInt(46));
        this.release = (1 + random.nextInt(3)) + "." + random.nextInt(12);
    }

    List<StoredPoint> points(Instant from, Instant until) {
        List<Run> runs = runs(from, until);
        List<StoredPoint> points = new ArrayList<>();
        for (Run run : runs) {
            points.add(point("pipeline_run", run.end(), run.values()));
            points.add(point("dora", run.end(), dora(run)));
        }
        runs.subList(max(0, runs.size() - 2), runs.size()).forEach(run -> points.addAll(evidence(run)));
        return points;
    }

    private List<Run> runs(Instant from, Instant until) {
        List<Run> runs = new ArrayList<>();
        long build = 1 + random.nextInt(60);
        boolean failing = false;
        for (Instant cursor = from; ; ) {
            double gapDays = -log(1 - random.nextDouble()) / profile.perDay();
            Instant start = workingHours(cursor.plusSeconds((long) (gapDays * 86_400)));
            Instant end = start.plusSeconds(60L * (profile.minMinutes()
                    + random.nextInt(profile.maxMinutes() - profile.minMinutes() + 1)));
            if (end.isAfter(until)) {
                return runs;
            }
            RunResult result = result(failing);
            failing = result == FAILURE;
            runs.add(new Run(start, end, build++, result, branch(), hex(6), profile.stages(), stages(result)));
            cursor = end;
        }
    }

    private Instant workingHours(Instant time) {
        ZonedDateTime at = time.atZone(UTC);
        if (at.getHour() < 6 || at.getHour() >= 21) {
            at = at.plusDays(at.getHour() >= 21 ? 1 : 0).withHour(6).withMinute(random.nextInt(60));
        }
        if (at.getDayOfWeek().getValue() >= SATURDAY.getValue() && random.nextDouble() < 0.8) {
            at = at.plusDays(8 - at.getDayOfWeek().getValue());
        }
        return at.toInstant().truncatedTo(SECONDS);
    }

    private RunResult result(boolean failing) {
        if (random.nextDouble() < (failing ? 0.45 : health)) {
            return SUCCESS;
        }
        double kind = random.nextDouble();
        return kind < 0.5 ? FAILURE : kind < 0.9 ? UNSTABLE : ABORTED;
    }

    private String branch() {
        double kind = random.nextDouble();
        if (kind < 0.55) {
            return "develop";
        }
        if (kind < 0.7) {
            return "main";
        }
        return kind < 0.92 ? "feature/" + FEATURES.get(random.nextInt(FEATURES.size())) : "release/" + release;
    }

    private List<CheckStatus> stages(RunResult result) {
        List<String> names = profile.stages();
        List<CheckStatus> statuses = new ArrayList<>(names.stream().map(name -> PASS).toList());
        int stopped = 1 + random.nextInt(names.size() - 1);
        switch (result) {
            case UNSTABLE -> {
                List<Integer> checks = new ArrayList<>();
                for (int i = 0; i < names.size(); i++) {
                    if (REASONS.containsKey(names.get(i)) && !names.get(i).startsWith("Deploy")
                            && !names.get(i).equals("Build") && !names.get(i).equals("Release Gate")) {
                        checks.add(i);
                    }
                }
                statuses.set(checks.get(random.nextInt(checks.size())), WARN);
            }
            case FAILURE -> {
                statuses.set(stopped, FAIL);
                for (int i = stopped + 1; i < names.size(); i++) {
                    statuses.set(i, SKIP);
                }
            }
            case ABORTED -> {
                for (int i = stopped; i < names.size(); i++) {
                    statuses.set(i, SKIP);
                }
            }
            default -> {
            }
        }
        return statuses;
    }

    private Map<String, String> dora(Run run) {
        boolean deployment = reached(run, "Deploy RD") && !run.branch().startsWith("feature/");
        long leadTime = deployment ? (long) (leadTimeSeconds * (0.4 + 1.2 * random.nextDouble())) : 0;
        return Map.of("deployment", flag(deployment),
                "change_failure", flag(deployment && run.result() == FAILURE),
                "lead_time_s", Long.toString(leadTime), "duration_s", Long.toString(run.seconds()));
    }

    private List<StoredPoint> evidence(Run run) {
        List<StoredPoint> points = new ArrayList<>();
        List<String> names = profile.stages();
        double[] weights = names.stream().mapToDouble(name -> EFFORT.get(name) * (0.7 + 0.6 * random.nextDouble()))
                .toArray();
        double sum = Arrays.stream(weights).sum();
        Instant stageEnd = run.start();
        for (int i = 0; i < names.size(); i++) {
            CheckStatus status = run.stages().get(i);
            long seconds = status == SKIP ? 0 : (long) (run.seconds() * weights[i] / sum);
            stageEnd = stageEnd.plusSeconds(seconds);
            Map<String, String> values = new HashMap<>(Map.of("stage", names.get(i), "status", tag(status),
                    "order", Integer.toString(i + 1), "duration_s", Long.toString(seconds)));
            reason(names.get(i), status).ifPresent(reason -> values.put("reason", reason));
            points.add(point("stage_event", stageEnd.isAfter(run.end()) ? run.end() : stageEnd, values));
        }
        for (String module : modules) {
            points.addAll(moduleEvidence(run, module));
        }
        policy(run, "AppScan SAST", "sast", points);
        policy(run, "Nexus IQ", "iast", points);
        policy(run, "SonarQube", "sonar", points);
        if (reached(run, "SonarQube")) {
            points.add(point("vulnerabilities", run.end(), Map.of("scanner", "sonar", "critical", "0",
                    "high", count(0, 1), "medium", count(0, 6), "low", count(2, 30))));
        }
        if (reached(run, "Nexus IQ")) {
            points.add(point("vulnerabilities", run.end(), Map.of("scanner", "nexusiq", "critical", "0",
                    "high", run.status("Nexus IQ") == PASS ? "0" : count(1, 3), "medium", count(0, 5),
                    "low", count(0, 9))));
        }
        long violations = run.stages().stream().filter(s -> s == WARN || s == FAIL).count();
        points.add(point("release_gate", run.end(), Map.of("allowed", run.result() == SUCCESS ? "yes" : "no",
                "violations", Long.toString(violations), "reason", firstProblem(run))));
        return points;
    }

    private List<StoredPoint> moduleEvidence(Run run, String module) {
        List<StoredPoint> points = new ArrayList<>();
        CheckStatus sonar = run.status("SonarQube");
        points.add(point("build_evidence", run.end(), Map.of("module", module,
                "artifact_version", release + "." + random.nextInt(10) + "-" + run.build(),
                "sonar_quality_gate", sonar == null || sonar == SKIP ? "NONE"
                        : sonar == PASS ? "OK" : sonar == WARN ? "WARN" : "ERROR",
                "config_rendered_at", run.start().plusSeconds(3).toString(), "config_sha256", hex(8))));
        TEST_SUITES.forEach((stage, suite) -> {
            if (reached(run, stage)) {
                int total = switch (suite) {
                    case "unit" -> 150 + random.nextInt(750);
                    case "smoke" -> 8 + random.nextInt(22);
                    case "regression" -> 40 + random.nextInt(220);
                    default -> 5 + random.nextInt(15);
                };
                int failed = run.status(stage) == PASS ? 0 : 1 + random.nextInt(4);
                int skipped = random.nextInt(max(1, total / 50));
                points.add(point("test_execution", run.end(), Map.of("module", module, "suite", suite,
                        "total", Integer.toString(total), "passed", Integer.toString(total - failed - skipped),
                        "failed", Integer.toString(failed), "skipped", Integer.toString(skipped),
                        "not_configured", "0", "duration_ms", Long.toString(total * (200L + random.nextInt(900))))));
            }
        });
        if (reached(run, "Unit Tests")) {
            double percent = round(580 + random.nextDouble() * 360) / 10.0;
            int lines = 4_000 + random.nextInt(36_000);
            points.add(point("code_coverage", run.end(), Map.of("module", module, "line_pct", Double.toString(percent),
                    "required", "70", "covered", Long.toString(round(lines * percent / 100)),
                    "total", Integer.toString(lines), "met", flag(percent >= 70), "measured", "yes")));
        }
        findings(run, module, "AppScan SAST", "sast", 2, points);
        findings(run, module, "AppScan DAST", "dast", 1, points);
        findings(run, module, "Nexus IQ", "niq", 0, points);
        return points;
    }

    private void findings(Run run, String module, String stage, String scanner, int maxHigh, List<StoredPoint> points) {
        if (!reached(run, stage)) {
            return;
        }
        CheckStatus status = run.status(stage);
        points.add(point("security_findings", run.end(), Map.of("module", module, "scanner", scanner,
                "critical", "0", "high", status == FAIL ? count(maxHigh + 1, maxHigh + 3)
                        : count(0, maxHigh), "medium", status == WARN ? count(11, 15) : count(0, 10),
                "low", count(0, 25), "max_critical", "0", "max_high", Integer.toString(maxHigh), "max_medium", "10",
                "status", tag(status))));
    }

    private void policy(Run run, String stage, String scanner, List<StoredPoint> points) {
        if (reached(run, stage)) {
            points.add(point("policy_status", run.end(), Map.of("scanner", scanner, "status", tag(run.status(stage)))));
        }
    }

    private String firstProblem(Run run) {
        for (int i = 0; i < run.stages().size(); i++) {
            CheckStatus status = run.stages().get(i);
            if (status == FAIL || status == WARN) {
                return profile.stages().get(i) + ": " + REASONS.get(profile.stages().get(i));
            }
        }
        return run.result() == ABORTED ? "The run was aborted" : "All checks passed";
    }

    private boolean reached(Run run, String stage) {
        CheckStatus status = run.status(stage);
        return status != null && status != SKIP;
    }

    private Optional<String> reason(String stage, CheckStatus status) {
        return status == WARN || status == FAIL
                ? Optional.ofNullable(REASONS.get(stage)) : Optional.empty();
    }

    private StoredPoint point(String measurement, Instant time, Map<String, String> values) {
        return new StoredPoint(measurement, tag, job, time, values);
    }

    private String count(int min, int max) {
        return Integer.toString(min + random.nextInt(max - min + 1));
    }

    private String hex(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    private static String tag(CheckStatus status) {
        return status.name().toLowerCase(ROOT);
    }

    private record Profile(double perDay, int minMinutes, int maxMinutes, List<String> stages) {
    }

    private record Run(Instant start, Instant end, long build, RunResult result, String branch, String commit,
                       List<String> names, List<CheckStatus> stages) {

        long seconds() {
            return end.getEpochSecond() - start.getEpochSecond();
        }

        CheckStatus status(String stage) {
            int index = names.indexOf(stage);
            return index < 0 ? null : stages.get(index);
        }

        Map<String, String> values() {
            return Map.ofEntries(entry("result", result.name()), entry("branch", branch),
                    entry("build", Long.toString(build)), entry("duration_s", Long.toString(seconds())),
                    entry("commit", commit), entry("stages_total", Integer.toString(stages.size())),
                    entry("passed", tally(PASS)), entry("warned", tally(WARN)),
                    entry("failed", tally(FAIL)), entry("blocked", "0"),
                    entry("skipped", tally(SKIP)));
        }

        private String tally(CheckStatus status) {
            return Long.toString(stages.stream().filter(stage -> stage == status).count());
        }
    }
}
