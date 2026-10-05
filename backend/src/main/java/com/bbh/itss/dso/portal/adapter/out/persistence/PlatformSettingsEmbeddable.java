package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record PlatformSettingsEmbeddable(
        @Column(name = "JENKINS_URL", length = 500) String jenkinsUrl,
        @Column(name = "JENKINS_LIBRARY", nullable = false, length = 200) String jenkinsLibrary,
        @Column(name = "ASOC_URL", nullable = false, length = 500) String asocUrl,
        @Column(name = "APPSCAN_CLIENT_LINUX_URL", nullable = false, length = 1000) String appScanClientLinuxUrl,
        @Column(name = "APPSCAN_CLIENT_WINDOWS_URL", nullable = false, length = 1000) String appScanClientWindowsUrl,
        @Column(name = "PROXY_HOST", length = 255) String proxyHost,
        @Column(name = "PROXY_PORT") Integer proxyPort,
        @Column(name = "PROXY_USER", length = 100) String proxyUser,
        @Column(name = "OIS_HOST", length = 255) String oisHost,
        @Column(name = "SONAR_SERVER_URL", nullable = false, length = 500) String sonarServerUrl,
        @Column(name = "SONAR_INSTALLATION_NAME", nullable = false, length = 200) String sonarInstallationName,
        @Column(name = "NEXUS_IQ_SERVER_URL", nullable = false, length = 500) String nexusIqServerUrl,
        @Column(name = "NEXUS_IQ_CREDENTIALS_ID", nullable = false, length = 200) String nexusIqCredentialsId,
        @Column(name = "NEXUS_SNAPSHOT_URL", length = 1000) String nexusSnapshotRepositoryUrl,
        @Column(name = "NEXUS_SNAPSHOT_REPOSITORY_ID", length = 200) String nexusSnapshotRepositoryId,
        @Column(name = "INFLUX_WRITE_URL", length = 1000) String influxWriteUrl,
        @Column(name = "INFLUX_CREDENTIALS_ID", length = 200) String influxCredentialsId,
        @Column(name = "IOS_BUILD_AGENT", length = 255) String iosBuildAgent) {

    static PlatformSettingsEmbeddable of(PlatformSettings platform) {
        return new PlatformSettingsEmbeddable(platform.jenkinsUrl(), platform.jenkinsLibrary(), platform.asocUrl(),
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
