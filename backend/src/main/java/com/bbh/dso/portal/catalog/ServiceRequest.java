package com.bbh.dso.portal.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One service as entered in the portal, grouped in the sections of its config.yaml entry. Optional sections
 * may be left out.
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
        @NotNull @Valid DeploymentSettings deployment,
        @NotNull @Valid AppScanSettings appScan,
        @Valid SonarSettings sonar,
        @Valid NexusIqSettings nexusIq,
        @Valid ScmSettings scm,
        @Valid MetricsSettings metrics,
        @Valid AdditionalConfig additionalConfig) {

    ServiceSettings settings() {
        return new ServiceSettings(build, deployment, appScan, sonar, nexusIq, scm, metrics, additionalConfig);
    }
}
