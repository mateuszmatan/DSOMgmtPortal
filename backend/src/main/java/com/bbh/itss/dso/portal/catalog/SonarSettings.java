package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record SonarSettings(
        @Size(max = 200)
        @Column(name = "SONAR_PROJECT_NAME", length = 200)
        String projectName,
        @Size(max = 400)
        @Pattern(regexp = "^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$",
                message = "may contain letters, digits, '-', '_', '.' and ':' with at least one non-digit")
        @Column(name = "SONAR_PROJECT_KEY", length = 400)
        String projectKey,
        @Size(max = 200)
        @Column(name = "SONAR_INSTALLATION_NAME", length = 200)
        String installationName,
        @Size(max = 200)
        @Column(name = "SONAR_CREDENTIALS_ID", length = 200)
        String credentialsId,
        @Size(max = 200)
        @Column(name = "SONAR_AUTH_TOKEN_CREDENTIALS_ID", length = 200)
        String authTokenCredentialsId,
        @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9_]*$", message = "must be a SonarQube badge token such as sqb_1a2b3c")
        @Column(name = "SONAR_BADGE_TOKEN", length = 200)
        String badgeToken,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SONAR_ADD_BADGES", nullable = false)
        Boolean addBadges,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SONAR_FULL_BADGES", nullable = false)
        Boolean fullBadges,
        @Valid
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "SONAR_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "SONAR_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "SONAR_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "SONAR_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "SONAR_ENVIRONMENT", length = 4000))
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
                .set("tools.sonar.badgeToken", badgeToken);
        if (addBadges) {
            config.set("tools.sonar.addBadges", true);
        }
        if (fullBadges) {
            config.set("tools.sonar.fullBadges", true);
        }
        command.writeTo(config, "tools.sonar", tool);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        if (tool != BuildTool.FLUTTER && projectKey != null && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals of the analysis, for example sonar:sonar"
                    : "add the Gradle tasks of the analysis, for example sonarqube");
        }
    }
}
