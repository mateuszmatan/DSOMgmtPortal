package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record DeploymentSettingsEmbeddable(
        @Enumerated(EnumType.STRING)
        @Column(name = "DEPLOY_TARGET", nullable = false, length = 20) DeployTarget target,
        @Column(name = "APP_NAME", length = 200) String appName,
        @Column(name = "ARTIFACT_NAME", length = 300) String artifactName,
        @Column(name = "BASE_ARTIFACT_NAME", length = 300) String baseArtifactName) {

    static DeploymentSettingsEmbeddable of(DeploymentSettings deployment) {
        return new DeploymentSettingsEmbeddable(deployment.target(), deployment.appName(), deployment.artifactName(),
                deployment.baseArtifactName());
    }

    DeploymentSettings toDomain() {
        return new DeploymentSettings(target, appName, artifactName, baseArtifactName);
    }
}
