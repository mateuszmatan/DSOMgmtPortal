package com.bbh.itss.dso.portal.catalog;

import java.util.List;
import java.util.Map;

/**
 * A stored service with every section of its settings, in the shape of {@link ServiceRequest}.
 */
public record ServiceResponse(
        Long id,
        String name,
        String description,
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

    static ServiceResponse from(ServiceDefinition service) {
        ServiceSettings s = service.settings();
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(), s.build(),
                s.unitTests(), s.tests(), s.testJobs(), s.deployment(), s.delivery(), s.urbanCode(),
                s.urbanCodeApplications(), s.sshTargets(), s.openShiftTargets(), s.appScan(), s.sonar(), s.nexusIq(),
                s.scm(), s.goldenFix(), s.metrics(), s.flutter());
    }
}
