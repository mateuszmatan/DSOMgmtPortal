package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.Text;
import com.bbh.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Where the service is deployed: {@code deployTarget}, plus {@code appName} and {@code artifactName} that
 * OpenShift deployments require.
 */
@Embeddable
public record DeploymentSettings(
        @NotNull
        @Enumerated(EnumType.STRING)
        @Column(name = "DEPLOY_TARGET", nullable = false, length = 20)
        DeployTarget target,
        @Size(max = 200)
        @Column(name = "APP_NAME", length = 200)
        String appName,
        @Size(max = 300)
        @Column(name = "ARTIFACT_NAME", length = 300)
        String artifactName) implements ConfigSection {

    public DeploymentSettings {
        appName = Text.trimToNull(appName);
        artifactName = Text.trimToNull(artifactName);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("deployTarget", target.configValue()).set("appName", appName).set("artifactName", artifactName);
    }

    @Override
    public void validate(ValidationProblems problems) {
        if (target == DeployTarget.OPENSHIFT) {
            if (appName == null) {
                problems.add("appName", "is required for OpenShift deployment");
            }
            if (artifactName == null) {
                problems.add("artifactName", "is required for OpenShift deployment");
            }
        }
    }
}
