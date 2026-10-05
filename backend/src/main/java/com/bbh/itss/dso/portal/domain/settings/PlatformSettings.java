package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.net.URI;
import java.util.Map;

public record PlatformSettings(String jenkinsUrl, String jenkinsLibrary, String asocUrl, String appScanClientLinuxUrl,
                               String appScanClientWindowsUrl, String proxyHost, Integer proxyPort, String proxyUser,
                               String oisHost, String sonarServerUrl, String sonarInstallationName,
                               String nexusIqServerUrl, String nexusIqCredentialsId, String nexusSnapshotRepositoryUrl,
                               String nexusSnapshotRepositoryId, String influxWriteUrl, String influxCredentialsId,
                               String iosBuildAgent) {

    public PlatformSettings {
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

    public PlatformSettings withJenkinsUrl(String url) {
        return new PlatformSettings(url, jenkinsLibrary, asocUrl, appScanClientLinuxUrl, appScanClientWindowsUrl,
                proxyHost, proxyPort, proxyUser, oisHost, sonarServerUrl, sonarInstallationName, nexusIqServerUrl,
                nexusIqCredentialsId, nexusSnapshotRepositoryUrl, nexusSnapshotRepositoryId, influxWriteUrl,
                influxCredentialsId, iosBuildAgent);
    }

    public Map<String, Object> toConfig() {
        Map<String, Object> environment = new ConfigTree().set("APPSCAN_SERVER_URL", asocUrl)
                .set("APPSCAN_HOST", host(asocUrl)).set("SA_LINUX_URL", appScanClientLinuxUrl)
                .set("SA_WIN_URL", appScanClientWindowsUrl).set("PROXY_HOST", proxyHost)
                .set("PROXY_PORT", proxyPort == null ? null : String.valueOf(proxyPort)).set("PROXY_USER", proxyUser)
                .toMap();
        return new ConfigTree()
                .set("jenkinsUrl", jenkinsUrl)
                .set("jenkinsLibrary", jenkinsLibrary)
                .set("environment", environment)
                .set("oisHost", oisHost)
                .set("nexusSnapshotRepositoryUrl", nexusSnapshotRepositoryUrl)
                .set("nexusSnapshotRepositoryId", nexusSnapshotRepositoryId)
                .set("iosBuildAgent", iosBuildAgent)
                .toMap();
    }

    public void writeProjectDefaults(ConfigTree config) {
        config.set("asoc.url", asocUrl)
                .set("influx.url", influxWriteUrl)
                .set("influx.credentialsId", influxCredentialsId)
                .set("tools.sonar.serverUrl", sonarServerUrl)
                .set("tools.sonar.installationName", sonarInstallationName)
                .set("tools.nexusIq.serverUrl", nexusIqServerUrl)
                .set("tools.nexusIq.credentialsId", nexusIqCredentialsId);
    }

    public void validate(ValidationProblems problems) {
        if (proxyHost != null && proxyPort == null) {
            problems.add("proxyPort", "is required with a proxy host");
        }
        if (proxyHost == null && proxyPort != null) {
            problems.add("proxyHost", "is required with a proxy port");
        }
    }

    private static String host(String url) {
        try {
            return url == null ? null : URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
