package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigSection;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

public record DeploymentSettings(
        DeployTarget target,
        String appName,
        String artifactName,
        String baseArtifactName) implements ConfigSection {

    public DeploymentSettings {
        appName = Text.trimToNull(appName);
        artifactName = Text.trimToNull(artifactName);
        baseArtifactName = Text.trimToNull(baseArtifactName);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("deployTarget", target.configValue())
                .set("appName", appName)
                .set("artifactName", artifactName)
                .set("baseArtifactName", baseArtifactName);
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
