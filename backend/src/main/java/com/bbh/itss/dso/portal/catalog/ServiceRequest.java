package com.bbh.itss.dso.portal.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * One service as entered in the portal, grouped in the sections of its project entry. Only the build, the
 * deployment target and the AppScan application are required; sections left out take their defaults.
 *
 * @param id null for a service to add
 */
public record ServiceRequest(
        Long id,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,99}$",
                message = "use lower case letters, digits, '.', '-' or '_', starting with a letter or digit")
        String name,
        @Size(max = 2000) String description,
        @NotNull @Valid BuildSettings build,
        @Valid UnitTestSettings unitTests,
        @Valid TestSettings tests,
        @Size(max = 100) List<@NotNull @Valid TestJob> testJobs,
        @NotNull @Valid DeploymentSettings deployment,
        @Valid ToolCommand delivery,
        @Valid UrbanCodeSettings urbanCode,
        @Size(max = 20) List<@NotNull @Valid UrbanCodeApplicationSettings> urbanCodeApplications,
        Map<Region, @Valid SshTarget> sshTargets,
        Map<Region, @Valid OpenShiftTarget> openShiftTargets,
        @NotNull @Valid AppScanSettings appScan,
        @Valid SonarSettings sonar,
        @Valid NexusIqSettings nexusIq,
        @Valid ScmSettings scm,
        @Valid GoldenFixPolicy goldenFix,
        @Valid MetricsSettings metrics,
        @Valid FlutterSettings flutter) {

    ServiceSettings settings() {
        return new ServiceSettings(build, unitTests, tests, testJobs, deployment, delivery, urbanCode,
                urbanCodeApplications, sshTargets, openShiftTargets, appScan, sonar, nexusIq, scm, goldenFix, metrics,
                flutter);
    }
}
