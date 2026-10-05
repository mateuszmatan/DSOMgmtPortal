package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.SonarSettings;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record SonarSettingsEmbeddable(
        @Column(name = "SONAR_PROJECT_NAME", length = 200) String projectName,
        @Column(name = "SONAR_PROJECT_KEY", length = 400) String projectKey,
        @Column(name = "SONAR_INSTALLATION_NAME", length = 200) String installationName,
        @Column(name = "SONAR_CREDENTIALS_ID", length = 200) String credentialsId,
        @Column(name = "SONAR_AUTH_TOKEN_CREDENTIALS_ID", length = 200) String authTokenCredentialsId,
        @Column(name = "SONAR_BADGE_TOKEN", length = 200) String badgeToken,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SONAR_ADD_BADGES", nullable = false) Boolean addBadges,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SONAR_FULL_BADGES", nullable = false) Boolean fullBadges,
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "SONAR_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "SONAR_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "SONAR_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "SONAR_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "SONAR_ENVIRONMENT", length = 4000))
        ToolCommandEmbeddable command) {

    static SonarSettingsEmbeddable of(SonarSettings sonar) {
        return new SonarSettingsEmbeddable(sonar.projectName(), sonar.projectKey(), sonar.installationName(),
                sonar.credentialsId(), sonar.authTokenCredentialsId(), sonar.badgeToken(), sonar.addBadges(),
                sonar.fullBadges(), ToolCommandEmbeddable.of(sonar.command()));
    }

    SonarSettings toDomain() {
        return new SonarSettings(projectName, projectKey, installationName, credentialsId, authTokenCredentialsId,
                badgeToken, addBadges, fullBadges, ToolCommandEmbeddable.toDomain(command));
    }
}
