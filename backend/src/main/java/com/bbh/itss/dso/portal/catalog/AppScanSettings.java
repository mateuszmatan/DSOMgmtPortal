package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.Locale;

/**
 * The service's HCL AppScan application, scanned by SAST and, when enabled, by DAST: {@code appId},
 * {@code sast.scanName} and the {@code dast} section.
 */
@Embeddable
public record AppScanSettings(
        @NotBlank
        @Pattern(regexp = "^\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*$",
                message = "must be the AppScan application ID, a UUID such as 109f44ac-cc06-4ca0-884e-d944904f7019")
        @Column(name = "APPSCAN_APP_ID", nullable = false, length = 36)
        String applicationId,
        @Size(max = 200)
        @Column(name = "SAST_SCAN_NAME", length = 200)
        String sastScanName,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "DAST_ENABLED", nullable = false)
        Boolean dastEnabled,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http or https URL")
        @Column(name = "DAST_TARGET_URL", length = 1000)
        String dastTargetUrl,
        @Size(max = 100)
        @Column(name = "DAST_PRESENCE_ID", length = 100)
        String dastPresenceId) implements ConfigSection {

    public AppScanSettings {
        applicationId = applicationId == null ? null : applicationId.trim().toLowerCase(Locale.ROOT);
        sastScanName = Text.trimToNull(sastScanName);
        dastEnabled = Boolean.TRUE.equals(dastEnabled);
        dastTargetUrl = Text.trimToNull(dastTargetUrl);
        dastPresenceId = Text.trimToNull(dastPresenceId);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("appId", applicationId)
                .set("sast.scanName", sastScanName)
                .set("dast.enabled", dastEnabled)
                .set("dast.targetUrl", dastTargetUrl)
                .set("dast.presenceId", dastPresenceId);
    }

    @Override
    public void validate(ValidationProblems problems) {
        if (dastEnabled && dastTargetUrl == null) {
            problems.add("dastTargetUrl", "is required when DAST is enabled");
        }
    }
}
