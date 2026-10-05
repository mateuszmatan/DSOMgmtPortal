package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.SonarSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SonarSettingsDto(
        @Size(max = 200)
        String projectName,
        @Size(max = 400)
        @Pattern(regexp = "^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$",
                message = "may contain letters, digits, '-', '_', '.' and ':' with at least one non-digit")
        String projectKey,
        @Size(max = 200)
        String installationName,
        @Size(max = 200)
        String credentialsId,
        @Size(max = 200)
        String authTokenCredentialsId,
        @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9_]*$", message = "must be a SonarQube badge token such as sqb_1a2b3c")
        String badgeToken,
        Boolean addBadges,
        Boolean fullBadges,
        @Valid
        ToolCommandDto command) {

    public SonarSettingsDto {
        projectName = Text.trimToNull(projectName);
        projectKey = Text.trimToNull(projectKey);
        installationName = Text.trimToNull(installationName);
        credentialsId = Text.trimToNull(credentialsId);
        authTokenCredentialsId = Text.trimToNull(authTokenCredentialsId);
        badgeToken = Text.trimToNull(badgeToken);
        addBadges = Boolean.TRUE.equals(addBadges);
        fullBadges = Boolean.TRUE.equals(fullBadges);
        command = command == null ? ToolCommandDto.NONE : command;
    }

    static SonarSettingsDto from(SonarSettings source) {
        return new SonarSettingsDto(source.projectName(), source.projectKey(), source.installationName(),
                source.credentialsId(), source.authTokenCredentialsId(), source.badgeToken(), source.addBadges(),
                source.fullBadges(), ToolCommandDto.from(source.command()));
    }

    SonarSettings toDomain() {
        return new SonarSettings(projectName, projectKey, installationName, credentialsId, authTokenCredentialsId,
                badgeToken, addBadges, fullBadges, command.toDomain());
    }
}
