package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;
import java.util.Locale;

/**
 * The service's HCL AppScan application, scanned by SAST and, when enabled, by DAST: {@code appId}, the folders
 * SAST scans or skips ({@code includedDirs}, {@code excludedDirs}), how the IRX is prepared ({@code asoc.*} and
 * the optional compile command {@code asoc.gradle} or {@code asoc.maven}), {@code sast.scanName} and the
 * {@code dast} section.
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
        @Size(max = 30)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "SAST_INCLUDED_DIRS", length = 2000)
        List<@Pattern(regexp = "^[^,]{1,300}$", message = "one folder per entry, without commas") String> includedDirs,
        @Size(max = 30)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "SAST_EXCLUDED_DIRS", length = 2000)
        List<@Pattern(regexp = "^[^,]{1,300}$", message = "one folder per entry, without commas") String> excludedDirs,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_COMPILE", nullable = false)
        Boolean compile,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_SOURCE_CODE_ONLY", nullable = false)
        Boolean sourceCodeOnly,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_USE_CONFIG_FILE", nullable = false)
        Boolean useConfigFile,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_INSECURE_TLS", nullable = false)
        Boolean insecureTls,
        @Size(max = 500)
        @Column(name = "APPSCAN_CLIENT_PATH", length = 500)
        String clientPath,
        @Valid
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "APPSCAN_COMPILE_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "APPSCAN_COMPILE_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "APPSCAN_COMPILE_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "APPSCAN_COMPILE_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "APPSCAN_COMPILE_ENVIRONMENT", length = 4000))
        ToolCommand compileCommand,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "DAST_ENABLED", nullable = false)
        Boolean dastEnabled,
        @Size(max = 200)
        @Column(name = "DAST_SCAN_NAME", length = 200)
        String dastScanName,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http or https URL")
        @Column(name = "DAST_TARGET_URL", length = 1000)
        String dastTargetUrl,
        @Size(max = 100)
        @Column(name = "DAST_PRESENCE_ID", length = 100)
        String dastPresenceId) {

    public AppScanSettings {
        applicationId = applicationId == null ? null : applicationId.trim().toLowerCase(Locale.ROOT);
        sastScanName = Text.trimToNull(sastScanName);
        includedDirs = DelimitedListConverter.clean(includedDirs);
        excludedDirs = DelimitedListConverter.clean(excludedDirs);
        compile = !Boolean.FALSE.equals(compile);
        sourceCodeOnly = Boolean.TRUE.equals(sourceCodeOnly);
        useConfigFile = Boolean.TRUE.equals(useConfigFile);
        insecureTls = Boolean.TRUE.equals(insecureTls);
        clientPath = Text.trimToNull(clientPath);
        compileCommand = compileCommand == null ? ToolCommand.NONE : compileCommand;
        dastEnabled = Boolean.TRUE.equals(dastEnabled);
        dastScanName = Text.trimToNull(dastScanName);
        dastTargetUrl = Text.trimToNull(dastTargetUrl);
        dastPresenceId = Text.trimToNull(dastPresenceId);
    }

    /** Settings that only name the application, with every other option at the library's default. */
    public static AppScanSettings of(String applicationId) {
        return new AppScanSettings(applicationId, null, List.of(), List.of(), true, false, false, false, null, null,
                false, null, null, null);
    }

    /** The folder lists are comma separated in the library, the IRX options are written when they differ from its defaults. */
    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("appId", applicationId)
                .set("includedDirs", String.join(",", includedDirs))
                .set("excludedDirs", String.join(",", excludedDirs))
                .set("appscanPath", clientPath);
        if (!compile) {
            config.set("asoc.doCompile", false);
        }
        if (sourceCodeOnly) {
            config.set("asoc.sourceCodeOnly", true);
        }
        if (useConfigFile) {
            config.set("asoc.useAppScanConfig", true);
        }
        if (insecureTls) {
            config.set("asoc.insecureTls", true);
        }
        compileCommand.writeTo(config, "asoc", tool);
        config.set("sast.scanName", sastScanName)
                .set("dast.enabled", dastEnabled)
                .set("dast.scanName", dastScanName)
                .set("dast.targetUrl", dastTargetUrl)
                .set("dast.presenceId", dastPresenceId);
    }

    public void validate(ValidationProblems problems) {
        if (dastEnabled && dastTargetUrl == null) {
            problems.add("dastTargetUrl", "is required when DAST is enabled");
        }
    }
}
