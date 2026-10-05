package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.HOST;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.HOST_MESSAGE;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;

public record PlatformSettingsDto(
        @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        String jenkinsUrl,
        @NotBlank @Size(max = 200)
        String jenkinsLibrary,
        @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        String asocUrl,
        @NotBlank @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        String appScanClientLinuxUrl,
        @NotBlank @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        String appScanClientWindowsUrl,
        @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE)
        String proxyHost,
        @Min(1) @Max(65535)
        Integer proxyPort,
        @Size(max = 100)
        String proxyUser,
        @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE)
        String oisHost,
        @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        String sonarServerUrl,
        @NotBlank @Size(max = 200)
        String sonarInstallationName,
        @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        String nexusIqServerUrl,
        @NotBlank @Size(max = 200)
        String nexusIqCredentialsId,
        @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        String nexusSnapshotRepositoryUrl,
        @Size(max = 200)
        String nexusSnapshotRepositoryId,
        @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        String influxWriteUrl,
        @Size(max = 200)
        String influxCredentialsId,
        @Size(max = 255)
        String iosBuildAgent) {

    public PlatformSettingsDto {
        jenkinsUrl = Text.trimToNull(jenkinsUrl);
        jenkinsLibrary = Text.trimToNull(jenkinsLibrary);
        asocUrl = Text.trimToNull(asocUrl);
        appScanClientLinuxUrl = Text.trimToNull(appScanClientLinuxUrl);
        appScanClientWindowsUrl = Text.trimToNull(appScanClientWindowsUrl);
        proxyHost = Text.trimToNull(proxyHost);
        proxyUser = Text.trimToNull(proxyUser);
        oisHost = Text.trimToNull(oisHost);
        sonarServerUrl = Text.trimToNull(sonarServerUrl);
        sonarInstallationName = Text.trimToNull(sonarInstallationName);
        nexusIqServerUrl = Text.trimToNull(nexusIqServerUrl);
        nexusIqCredentialsId = Text.trimToNull(nexusIqCredentialsId);
        nexusSnapshotRepositoryUrl = Text.trimToNull(nexusSnapshotRepositoryUrl);
        nexusSnapshotRepositoryId = Text.trimToNull(nexusSnapshotRepositoryId);
        influxWriteUrl = Text.trimToNull(influxWriteUrl);
        influxCredentialsId = Text.trimToNull(influxCredentialsId);
        iosBuildAgent = Text.trimToNull(iosBuildAgent);
    }

    static PlatformSettingsDto from(PlatformSettings platform) {
        return new PlatformSettingsDto(platform.jenkinsUrl(), platform.jenkinsLibrary(), platform.asocUrl(),
                platform.appScanClientLinuxUrl(), platform.appScanClientWindowsUrl(), platform.proxyHost(),
                platform.proxyPort(), platform.proxyUser(), platform.oisHost(), platform.sonarServerUrl(),
                platform.sonarInstallationName(), platform.nexusIqServerUrl(), platform.nexusIqCredentialsId(),
                platform.nexusSnapshotRepositoryUrl(), platform.nexusSnapshotRepositoryId(), platform.influxWriteUrl(),
                platform.influxCredentialsId(), platform.iosBuildAgent());
    }

    PlatformSettings toDomain() {
        return new PlatformSettings(jenkinsUrl, jenkinsLibrary, asocUrl, appScanClientLinuxUrl, appScanClientWindowsUrl,
                proxyHost, proxyPort, proxyUser, oisHost, sonarServerUrl, sonarInstallationName, nexusIqServerUrl,
                nexusIqCredentialsId, nexusSnapshotRepositoryUrl, nexusSnapshotRepositoryId, influxWriteUrl,
                influxCredentialsId, iosBuildAgent);
    }
}
