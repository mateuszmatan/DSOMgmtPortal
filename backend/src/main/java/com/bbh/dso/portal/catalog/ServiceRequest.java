package com.bbh.dso.portal.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One service as entered in the portal. Field names follow the config.yaml keys of the DevSecOps library.
 */
public record ServiceRequest(
        Long id,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,99}$",
                message = "use lower case letters, digits, '.', '-' or '_', starting with a letter or digit")
        String name,
        @Size(max = 2000) String description,
        @NotNull BuildTool buildTool,
        @NotNull DeployTarget deployTarget,
        @Size(max = 500) String sourceDir,
        @Size(max = 500) String javaPath,
        Boolean buildToolAutoSetup,
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be the AppScan application ID, a UUID such as 109f44ac-cc06-4ca0-884e-d944904f7019")
        String appScanAppId,
        @Size(max = 200) String sastScanName,
        Boolean dastEnabled,
        @Size(max = 1000) @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http or https URL")
        String dastTargetUrl,
        @Size(max = 100) String dastPresenceId,
        @Size(max = 200) String sonarProjectName,
        @Size(max = 400) @Pattern(regexp = "^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$",
                message = "may contain letters, digits, '-', '_', '.' and ':' with at least one non-digit")
        String sonarProjectKey,
        @Size(max = 200) String nexusIqApplication,
        List<@NotBlank @Size(max = 300) String> nexusIqScanPatterns,
        @Size(max = 1000) @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http or https URL")
        String repositoryUrl,
        @Size(max = 200) String bitbucketCredentialsId,
        Boolean goldenFixEnabled,
        Boolean metricsEnabled,
        @Size(max = 200) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
        String influxProject,
        @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
        String influxEnv,
        @Size(max = 200) String appName,
        @Size(max = 300) String artifactName,
        @Size(max = 100_000) String additionalConfig) {

    /** Switches left out of the request take the library's defaults: GoldenFix and metrics on, the rest off. */
    public ServiceRequest {
        buildToolAutoSetup = Boolean.TRUE.equals(buildToolAutoSetup);
        dastEnabled = Boolean.TRUE.equals(dastEnabled);
        goldenFixEnabled = !Boolean.FALSE.equals(goldenFixEnabled);
        metricsEnabled = !Boolean.FALSE.equals(metricsEnabled);
    }
}
