package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record GlobalSettingsValues(
        PlatformSettings platform,
        DeploymentDefaults deployment,
        Map<Scanner, SeverityLimits> limits,
        ScanSettings scans,
        ReleaseGateSettings releaseGate,
        ServiceDefaults serviceDefaults,
        GoldenFixPolicy goldenFix) {

    public GlobalSettingsValues {
        Map<Scanner, SeverityLimits> ordered = new EnumMap<>(Scanner.class);
        if (limits != null) {
            limits.forEach((scanner, value) -> {
                if (scanner != null && value != null) {
                    ordered.put(scanner, value);
                }
            });
        }
        limits = Collections.unmodifiableMap(ordered);
    }

    public static GlobalSettingsValues bbhDefaults() {
        PlatformSettings platform = new PlatformSettings(null, "DevSecOpsJenkinsLibrary",
                "https://bbh.cloud.appscan.com",
                "https://tools.bbh.com/nexus/repository/releases/com/bbh/appscan/SAClientUtil/8.0.1646_Linux/"
                        + "SAClientUtil-8.0.1646_Linux-SAClientUtil_8.0.1646_Linux.zip",
                "https://tools.bbh.com/nexus/repository/releases/com/bbh/appscan/SAClientUtil/8.0.1646_Win/"
                        + "SAClientUtil-8.0.1646_Win-SAClientUtil_8.0.1646_Win.zip",
                "tstproxy.bbh.com", 9090, "PROXY_ASOCJenk", "oisapi.bbh.com",
                "https://tools.bbh.com/sonar", "SonarQube", "https://tools.bbh.com/IQ", "nexusiqP",
                "http://tools.bbh.com/nexus/content/repositories/snapshots/", "bbh-snapshots",
                "http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s",
                "influxdb-token", "mac002.bbh.com");
        DeploymentDefaults deployment = new DeploymentDefaults("deploy.bbh.com", "tomcat-app-process",
                "rdltaapps1.testbbh.com", "qcltaapps1.testbbh.com", "taadmin",
                "scripts/deployment/zero-downtime-deployment.sh", "scripts/deployment/version.properties");
        Map<Scanner, SeverityLimits> limits = new EnumMap<>(Scanner.class);
        for (Scanner scanner : Scanner.values()) {
            limits.put(scanner, SeverityLimits.ZERO);
        }
        ScanSettings scans = new ScanSettings(60, 120, 50, 30, true, 40, 30, 60, 60, 30, 30, true, 5);
        ReleaseGateSettings releaseGate = new ReleaseGateSettings(List.of(Scanner.values()), true,
                "release-gate.json");
        ServiceDefaults serviceDefaults = new ServiceDefaults(BuildTool.GRADLE,
                DeployTarget.VM, ".", 20);
        GoldenFixPolicy goldenFix = new GoldenFixPolicy(true, true, 2, List.of("maven", "npm", "pypi"),
                List.of("recommended-non-breaking-with-dependencies", "recommended-non-breaking"),
                List.of(), true, 3, 20, null, null, null, null, null, "DevSecOps GoldenFix",
                "devsecops-goldenfix@noreply.local", null);
        return new GlobalSettingsValues(platform, deployment, limits, scans, releaseGate, serviceDefaults, goldenFix);
    }

    public GlobalSettingsValues withPlatform(PlatformSettings changed) {
        return new GlobalSettingsValues(changed, deployment, limits, scans, releaseGate, serviceDefaults, goldenFix);
    }

    public void validate(ValidationProblems problems) {
        platform.validate(problems.at("platform"));
        for (Scanner scanner : Scanner.values()) {
            if (!limits.containsKey(scanner)) {
                problems.add("limits." + scanner.name(), "set the limits of every scanner");
            }
        }
        goldenFix.validateComplete(problems.at("goldenFix"));
    }

    public Map<String, Object> defaultsConfig() {
        ConfigTree defaults = new ConfigTree();
        serviceDefaults.writeTo(defaults);
        scans.writeTo(defaults);
        limits.forEach((scanner, value) -> value.writeTo(defaults, scanner.defaultsPath()));
        releaseGate.writeTo(defaults);
        goldenFix.writeTo(defaults);
        return defaults.toMap(List.of("buildTool", "deployTarget", "sourceDir", "coverage", "tools", "sast",
                "sca", "dast", "tests", "releaseGate", "goldenFix"));
    }
}
