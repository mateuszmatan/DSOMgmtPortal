package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeploymentSettingsDto(
        @NotNull
        DeployTarget target,
        @Size(max = 200)
        String appName,
        @Size(max = 300)
        String artifactName,
        @Size(max = 300)
        String baseArtifactName) {

    public DeploymentSettingsDto {
        appName = Text.trimToNull(appName);
        artifactName = Text.trimToNull(artifactName);
        baseArtifactName = Text.trimToNull(baseArtifactName);
    }

    static DeploymentSettingsDto from(DeploymentSettings source) {
        return new DeploymentSettingsDto(source.target(), source.appName(), source.artifactName(),
                source.baseArtifactName());
    }

    DeploymentSettings toDomain() {
        return new DeploymentSettings(target, appName, artifactName, baseArtifactName);
    }
}
