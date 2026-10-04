package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The service's SonarQube project ({@code tools.sonar}). Without a project key the library skips the scan.
 */
@Embeddable
public record SonarSettings(
        @Size(max = 200)
        @Column(name = "SONAR_PROJECT_NAME", length = 200)
        String projectName,
        @Size(max = 400)
        @Pattern(regexp = "^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$",
                message = "may contain letters, digits, '-', '_', '.' and ':' with at least one non-digit")
        @Column(name = "SONAR_PROJECT_KEY", length = 400)
        String projectKey) implements ConfigSection {

    public static final SonarSettings NONE = new SonarSettings(null, null);

    public SonarSettings {
        projectName = Text.trimToNull(projectName);
        projectKey = Text.trimToNull(projectKey);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("tools.sonar.projectName", projectName).set("tools.sonar.projectKey", projectKey);
    }
}
