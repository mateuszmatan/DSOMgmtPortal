package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public record ServiceSettings(
        BuildSettings build,
        UnitTestSettings unitTests,
        TestSettings tests,
        List<TestJob> testJobs,
        DeploymentSettings deployment,
        ToolCommand delivery,
        UrbanCodeSettings urbanCode,
        List<UrbanCodeApplicationSettings> urbanCodeApplications,
        Map<Region, SshTarget> sshTargets,
        Map<Region, OpenShiftTarget> openShiftTargets,
        AppScanSettings appScan,
        SonarSettings sonar,
        NexusIqSettings nexusIq,
        ScmSettings scm,
        GoldenFixPolicy goldenFix,
        MetricsSettings metrics,
        FlutterSettings flutter) {

    public ServiceSettings {
        Objects.requireNonNull(build, "a service needs its build settings");
        Objects.requireNonNull(deployment, "a service needs its deployment settings");
        Objects.requireNonNull(appScan, "a service needs its AppScan settings");
        unitTests = unitTests == null ? UnitTestSettings.NONE : unitTests;
        tests = tests == null ? TestSettings.DEFAULTS : tests;
        testJobs = testJobs == null ? List.of() : List.copyOf(testJobs);
        delivery = delivery == null ? ToolCommand.NONE : delivery;
        urbanCode = urbanCode == null ? UrbanCodeSettings.DEFAULTS : urbanCode;
        urbanCodeApplications = urbanCodeApplications == null ? List.of() : List.copyOf(urbanCodeApplications);
        sshTargets = withoutEmpty(sshTargets, SshTarget::isEmpty);
        openShiftTargets = withoutEmpty(openShiftTargets, OpenShiftTarget::isEmpty);
        sonar = sonar == null ? SonarSettings.NONE : sonar;
        nexusIq = nexusIq == null ? NexusIqSettings.NONE : nexusIq;
        scm = scm == null ? ScmSettings.NONE : scm;
        goldenFix = goldenFix == null ? GoldenFixPolicy.INHERITED : goldenFix;
        metrics = metrics == null ? MetricsSettings.DEFAULTS : metrics;
        flutter = flutter == null ? FlutterSettings.NONE : flutter;
    }

    public static ServiceSettings of(BuildSettings build, DeploymentSettings deployment, AppScanSettings appScan) {
        return new ServiceSettings(build, null, null, null, deployment, null, null, null, null, null, appScan, null,
                null, null, null, null, null);
    }

    public ServiceSettings withDefaultMetricsProject(String productCode, String serviceName) {
        MetricsSettings defaulted = metrics.withDefaultProject(productCode, serviceName);
        if (defaulted == metrics) {
            return this;
        }
        return new ServiceSettings(build, unitTests, tests, testJobs, deployment, delivery, urbanCode,
                urbanCodeApplications, sshTargets, openShiftTargets, appScan, sonar, nexusIq, scm, goldenFix, defaulted,
                flutter);
    }

    public void writeTo(ConfigTree config) {
        BuildTool tool = build.tool();
        build.writeTo(config);
        deployment.writeTo(config);
        appScan.writeTo(config, tool);
        metrics.writeTo(config);
        unitTests.writeTo(config, tool);
        sonar.writeTo(config, tool);
        nexusIq.writeTo(config);
        scm.writeTo(config);
        goldenFix.writeTo(config);
        tests.writeTo(config, testJobs);
        if (deployment.target() == DeployTarget.VM) {
            if (tool == BuildTool.MAVEN) {
                delivery.writeTo(config, "delivery", BuildTool.MAVEN);
            }
            urbanCode.writeTo(config, urbanCodeApplications);
            sshTargets.forEach((region, target) -> config.set("deploy.vm." + region.configKey(), target.toConfig()));
        } else {
            openShiftTargets.forEach((region, target) ->
                    config.set("deploy.openshift." + region.configKey(), target.toConfig()));
        }
        if (tool == BuildTool.FLUTTER) {
            flutter.writeTo(config);
        }
    }

    public void validate(ValidationProblems problems) {
        BuildTool tool = build.tool();
        build.validate(problems.at("build"));
        unitTests.validate(problems.at("unitTests"), tool);
        deployment.validate(problems.at("deployment"));
        appScan.validate(problems.at("appScan"));
        sonar.validate(problems.at("sonar"), tool);
        scm.validate(problems.at("scm"));
        metrics.validate(problems.at("metrics"));
        if (tool == BuildTool.FLUTTER) {
            flutter.validate(problems.at("flutter"));
        }
        if (deployment.target() == DeployTarget.VM && tool == BuildTool.MAVEN && delivery.tasks().isEmpty()) {
            problems.add("delivery.tasks", "add the Maven goals that upload the snapshot, for example deploy:deploy-file");
        }
        for (int i = 0; i < testJobs.size(); i++) {
            if (testJobs.get(i).needsRemoteJenkins()) {
                problems.add("testJobs[" + i + "].remoteJenkins",
                        "name the remote Jenkins or its URL, or give the job as a full URL");
            }
        }
    }

    private static <T> Map<Region, T> withoutEmpty(Map<Region, T> targets, Predicate<T> empty) {
        if (targets == null) {
            return Map.of();
        }
        Map<Region, T> kept = new EnumMap<>(Region.class);
        targets.forEach((region, target) -> {
            if (region != null && target != null && !empty.test(target)) {
                kept.put(region, target);
            }
        });
        return Collections.unmodifiableMap(kept);
    }
}
