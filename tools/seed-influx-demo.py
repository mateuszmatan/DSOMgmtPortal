#!/usr/bin/env python3
"""Write demo DevSecOps metrics to a local InfluxDB so the portal's monitoring pages have data to show.

The points have the same measurements, tags and fields the DevSecOps library writes from
com.bbh.metrics.PipelineMetrics: pipeline_run, dora, stage_event, security_findings, policy_status,
code_coverage, test_execution, test_job, release_gate and the legacy vulnerabilities. Like the library,
every point of a run carries the time the run finished, except the stage events.

By default the pipelines are read from a running portal, so every pipeline defined there gets a history:

    python3 tools/seed-influx-demo.py --portal http://localhost:8080

Pipelines can also be given directly as <project tag>:<env>:<variant>:

    python3 tools/seed-influx-demo.py --pipeline CERTSCANNER-gui:test:full

Only for local use: it writes random data.
"""
import argparse
import json
import random
import sys
import time
import urllib.request

STAGES = [
    "Monitor source changes (download sources)",
    "Unit tests",
    "Dependencies scan (Nexus IQ)",
    "SAST - Static Application Security Tests - HCL AppScan",
    "SCA (SonarQube)",
    "Nexus delivery (Static analysis passed)",
    "Lower test region deployment",
    "Regression tests (>60% user stories coverage)",
    "Smoke tests",
    "Performance tests",
    "DAST - Dynamic Application Security Tests - HCL AppScan",
    "Nexus delivery - Safe Artifact - 0 known Security Vulnerabilities",
]
STAGE_SECONDS = [20, 240, 90, 900, 180, 60, 300, 600, 240, 900, 1500, 60]
SCANNERS = ["sast", "sca", "niq", "dast"]
TEST_SUITES = ["Smoke tests", "Regression tests (>60% user stories coverage)", "Performance tests"]


def tag(value):
    return str(value).replace(",", r"\,").replace("=", r"\=").replace(" ", r"\ ")


def line(measurement, tags, fields, ts):
    tag_part = ",".join(f"{k}={tag(v)}" for k, v in tags.items() if v)
    field_part = ",".join(f"{k}={v}" for k, v in fields.items())
    return f"{measurement},{tag_part} {field_part} {ts}"


def text(value):
    return '"' + str(value).replace('"', '\\"') + '"'


def portal_pipelines(portal):
    def get(path):
        with urllib.request.urlopen(portal.rstrip("/") + path) as response:
            return json.load(response)

    result = []
    for product in get("/api/products"):
        for service in get(f"/api/products/{product['id']}/pipelines"):
            for pipeline in service["pipelines"]:
                result.append((pipeline["influxProjectTag"], pipeline["influxEnv"], pipeline["type"].lower(),
                               service["serviceName"]))
    return result


def runs_for(project, env, variant, module, days, rng):
    lines = []
    now = int(time.time())
    build = 1
    reliability = rng.uniform(0.6, 0.95)
    for day in range(days, -1, -1):
        for _ in range(rng.choice([0, 1, 1, 2, 2, 3, 4])):
            end = now - day * 86400 - rng.randint(0, 80000)
            if end > now:
                continue
            roll = rng.random()
            result = ("SUCCESS" if roll < reliability else "UNSTABLE" if roll < reliability + 0.12
                      else "FAILURE" if roll < 0.97 else "ABORTED")
            failed = result != "SUCCESS"
            stages = STAGES if variant in ("full", "extended") else STAGES[:5]
            durations = [max(5, int(s * rng.uniform(0.6, 1.5))) for s in STAGE_SECONDS[:len(stages)]]
            total = sum(durations)
            broken_stage = rng.randrange(1, len(stages)) if failed else None
            passed = warned = failed_count = 0
            t = end - total
            for index, (stage, seconds) in enumerate(zip(stages, durations)):
                t += seconds
                status = "PASS"
                if broken_stage is not None and index == broken_stage:
                    status = "FAIL" if result == "FAILURE" else "WARN"
                if status == "PASS":
                    passed += 1
                elif status == "WARN":
                    warned += 1
                else:
                    failed_count += 1
                reason = "" if status == "PASS" else rng.choice(
                    ["Coverage 54% below the required 60%", "2 high findings above policy",
                     "script returned exit code 1", "Quality gate failed"])
                lines.append(line("stage_event", {"project": project, "env": env, "stage": stage, "status": status}, {
                    "duration_ms": f"{seconds * 1000}i", "duration_s": f"{seconds}i",
                    "ok": "1i" if status == "PASS" else "0i", "executed": "1i", "order": f"{index + 1}i",
                    "reason": text(reason)}, t))
            deployed = (not failed or result == "UNSTABLE") and variant in ("full", "extended") and rng.random() < 0.8
            lead = total + rng.randint(600, 3 * 86400)
            lines.append(line("pipeline_run", {"project": project, "env": env, "variant": variant, "result": result,
                                               "branch": rng.choice(["develop", "develop", "main", "feature/login"])}, {
                "build": f"{build}i", "duration_s": f"{total}i", "success": "0i" if failed else "1i",
                "unstable": "1i" if result == "UNSTABLE" else "0i", "stages_total": f"{len(stages)}i",
                "passed": f"{passed}i", "warned": f"{warned}i", "failed": f"{failed_count}i", "blocked": "0i",
                "skipped": "0i", "not_required": "0i", "modules": "1i",
                "commit": text("%012x" % rng.getrandbits(48)), "job": text(f"{project}/{variant}")}, end))
            lines.append(line("dora", {"project": project, "env": env, "variant": variant}, {
                "deployment": "1i" if deployed else "0i", "change_failure": "1i" if failed else "0i",
                "lead_time_s": f"{lead}i", "duration_s": f"{total}i", "released": "1i" if not failed else "0i"}, end))
            broken = stages[broken_stage] if broken_stage is not None else None
            counts = {}
            for scanner in SCANNERS:
                high = rng.choice([0, 0, 0, 1, 2]) if failed else 0
                counts[scanner] = (0, high, rng.randint(0, 6), rng.randint(0, 20))
                critical, high, medium, low = counts[scanner]
                lines.append(line("security_findings", {"project": project, "env": env, "module": module,
                                                        "scanner": scanner, "status": "FAIL" if high else "PASS"}, {
                    "critical": f"{critical}i", "high": f"{high}i", "medium": f"{medium}i", "low": f"{low}i",
                    "total": f"{critical + high + medium + low}i", "above_policy": f"{high}i", "max_critical": "0i",
                    "max_high": "0i", "max_medium": "10i", "exceeded": "1i" if high else "0i"}, end))
            for scanner, key in (("sast", "sast"), ("dast", "dast"), ("nexusiq", "niq"), ("sonar", "sca")):
                critical, high, medium, low = counts[key]
                lines.append(line("vulnerabilities", {"project": project, "env": env, "scanner": scanner}, {
                    "critical": f"{critical}i", "high": f"{high}i", "medium": f"{medium}i", "low": f"{low}i"}, end))
            line_pct = round(rng.uniform(52, 93), 1)
            total_lines = rng.randint(4000, 30000)
            covered = int(total_lines * line_pct / 100)
            lines.append(line("code_coverage", {"project": project, "env": env, "module": module, "measured": "yes"}, {
                "line_pct": line_pct, "covered": f"{covered}i", "missed": f"{total_lines - covered}i",
                "total": f"{total_lines}i", "required": 60.0, "met": "1i" if line_pct >= 60 else "0i",
                "gap": round(max(0.0, 60 - line_pct), 1)}, end))
            policy = {"sast": counts["sast"][1], "sca": counts["sca"][1], "iast": counts["niq"][1],
                      "dast": counts["dast"][1] if variant in ("full", "extended") else None,
                      "coverage": 0 if line_pct >= 60 else 1, "sonar": 1 if broken == "SCA (SonarQube)" else 0}
            for scanner, problems in policy.items():
                status = "NOT_REQUIRED" if problems is None else "WARN" if problems else "PASS"
                lines.append(line("policy_status", {"project": project, "env": env, "scanner": scanner,
                                                    "status": status}, {
                    "ok": "0i" if status == "WARN" else "1i", "measured": "1i"}, end))
            if variant in ("full", "extended"):
                for suite in TEST_SUITES:
                    jobs = rng.randint(1, 3)
                    broken_jobs = 1 if broken == suite else 0
                    lines.append(line("test_execution", {"project": project, "env": env, "module": module,
                                                         "suite": suite}, {
                        "total": f"{jobs}i", "passed": f"{jobs - broken_jobs}i", "failed": f"{broken_jobs}i",
                        "not_configured": "0i", "duration_ms": f"{rng.randint(60, 900) * 1000}i",
                        "success_rate": round((jobs - broken_jobs) * 100.0 / jobs, 1)}, end))
                    lines.append(line("test_job", {"project": project, "env": env, "module": module, "suite": suite,
                                                   "status": "FAILURE" if broken_jobs else "SUCCESS", "type": "local"}, {
                        "duration_ms": f"{rng.randint(60, 900) * 1000}i", "name": text(f"{module}-{suite.split()[0].lower()}"),
                        "ok": "0i" if broken_jobs else "1i"}, end))
            violations = sum(1 for key in SCANNERS if counts[key][1]) + (0 if line_pct >= 60 else 1) + (1 if broken else 0)
            reason = "" if not violations else rng.choice(
                ["sast high 2 > 0", f"line coverage {line_pct}% below the required 60%", f"stage '{broken}' is WARN"])
            # The library writes "allowed" both as a tag and as a field.
            lines.append(line("release_gate", {"project": project, "env": env, "allowed": "no" if violations else "yes"}, {
                "allowed": "0i" if violations else "1i", "blocked": "1i" if violations else "0i",
                "violations": f"{violations}i", "reason": text(reason)}, end))
            build += 1
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8086")
    parser.add_argument("--org", default="DevSecOps")
    parser.add_argument("--bucket", default="DORA-metrics")
    parser.add_argument("--token", default="dso-local-token")
    parser.add_argument("--portal", help="portal base URL to read the pipelines from")
    parser.add_argument("--pipeline", action="append", default=[], help="<project tag>:<env>:<variant>")
    parser.add_argument("--days", type=int, default=90)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    pipelines = [tuple(p.split(":")) + (p.split(":")[0],) for p in args.pipeline]
    if args.portal:
        pipelines += portal_pipelines(args.portal)
    if not pipelines:
        sys.exit("No pipelines: pass --portal or --pipeline")

    rng = random.Random(args.seed)
    lines = []
    for project, env, variant, module in pipelines:
        lines += runs_for(project, env, variant, module, args.days, rng)

    url = f"{args.url.rstrip('/')}/api/v2/write?org={args.org}&bucket={args.bucket}&precision=s"
    for start in range(0, len(lines), 5000):
        request = urllib.request.Request(url, data="\n".join(lines[start:start + 5000]).encode(), method="POST",
                                         headers={"Authorization": f"Token {args.token}",
                                                  "Content-Type": "text/plain; charset=utf-8"})
        urllib.request.urlopen(request).close()
    print(f"Wrote {len(lines)} points for {len(pipelines)} pipeline(s)")


if __name__ == "__main__":
    main()
