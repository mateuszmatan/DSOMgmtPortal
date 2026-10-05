package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

public record SonarSettings(String projectName, String projectKey, String installationName, String credentialsId,
                            String authTokenCredentialsId, String badgeToken, Boolean addBadges, Boolean fullBadges,
                            ToolCommand command) {

    public static final SonarSettings NONE = new SonarSettings(null, null, null, null, null, null, false, false, null);

    public SonarSettings {
        projectName = Text.trimToNull(projectName);
        projectKey = Text.trimToNull(projectKey);
        installationName = Text.trimToNull(installationName);
        credentialsId = Text.trimToNull(credentialsId);
        authTokenCredentialsId = Text.trimToNull(authTokenCredentialsId);
        badgeToken = Text.trimToNull(badgeToken);
        addBadges = Boolean.TRUE.equals(addBadges);
        fullBadges = Boolean.TRUE.equals(fullBadges);
        command = command == null ? ToolCommand.NONE : command;
    }

    public static SonarSettings of(String projectName, String projectKey, ToolCommand command) {
        return new SonarSettings(projectName, projectKey, null, null, null, null, false, false, command);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("tools.sonar.projectName", projectName)
                .set("tools.sonar.projectKey", projectKey)
                .set("tools.sonar.installationName", installationName)
                .set("tools.sonar.credentialsId", credentialsId)
                .set("tools.sonar.authToken", authTokenCredentialsId)
                .set("tools.sonar.badgeToken", badgeToken)
                .flag("tools.sonar.addBadges", addBadges)
                .flag("tools.sonar.fullBadges", fullBadges);
        command.writeTo(config, "tools.sonar", tool);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        if (tool != BuildTool.FLUTTER && projectKey != null && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals of the analysis, for example sonar:sonar"
                    : "add the Gradle tasks of the analysis, for example sonarqube");
        }
        command.validate(problems.at("command"));
    }
}
