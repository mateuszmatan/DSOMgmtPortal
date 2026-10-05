package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record AppScanSettingsEmbeddable(
        @Column(name = "APPSCAN_APP_ID", nullable = false, length = 36) String applicationId,
        @Column(name = "SAST_SCAN_NAME", length = 200) String sastScanName,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "SAST_INCLUDED_DIRS", length = 2000) List<String> includedDirs,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "SAST_EXCLUDED_DIRS", length = 2000) List<String> excludedDirs,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_COMPILE", nullable = false) Boolean compile,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_SOURCE_CODE_ONLY", nullable = false) Boolean sourceCodeOnly,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_USE_CONFIG_FILE", nullable = false) Boolean useConfigFile,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "APPSCAN_INSECURE_TLS", nullable = false) Boolean insecureTls,
        @Column(name = "APPSCAN_CLIENT_PATH", length = 500) String clientPath,
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "APPSCAN_COMPILE_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "APPSCAN_COMPILE_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "APPSCAN_COMPILE_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "APPSCAN_COMPILE_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "APPSCAN_COMPILE_ENVIRONMENT", length = 4000))
        ToolCommandEmbeddable compileCommand,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "DAST_ENABLED", nullable = false) Boolean dastEnabled,
        @Column(name = "DAST_SCAN_NAME", length = 200) String dastScanName,
        @Column(name = "DAST_TARGET_URL", length = 1000) String dastTargetUrl,
        @Column(name = "DAST_PRESENCE_ID", length = 100) String dastPresenceId) {

    static AppScanSettingsEmbeddable of(AppScanSettings appScan) {
        return new AppScanSettingsEmbeddable(appScan.applicationId(), appScan.sastScanName(), appScan.includedDirs(),
                appScan.excludedDirs(), appScan.compile(), appScan.sourceCodeOnly(), appScan.useConfigFile(),
                appScan.insecureTls(), appScan.clientPath(), ToolCommandEmbeddable.of(appScan.compileCommand()),
                appScan.dastEnabled(), appScan.dastScanName(), appScan.dastTargetUrl(), appScan.dastPresenceId());
    }

    AppScanSettings toDomain() {
        return new AppScanSettings(applicationId, sastScanName, includedDirs, excludedDirs, compile, sourceCodeOnly,
                useConfigFile, insecureTls, clientPath, ToolCommandEmbeddable.toDomain(compileCommand), dastEnabled,
                dastScanName, dastTargetUrl, dastPresenceId);
    }
}
