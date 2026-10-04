package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.ConfigTree;
import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The BBH tools every pipeline uses: Jenkins and the name of the shared library in it, HCL AppScan on Cloud and
 * how it is reached through the BBH proxy, SonarQube, Nexus IQ, the Nexus snapshot repository, the InfluxDB the
 * metrics go to and the agent iOS builds run on. Credentials are named, never stored.
 */
@Embeddable
public record PlatformSettings(
        @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "JENKINS_URL", length = 500)
        String jenkinsUrl,
        @NotBlank @Size(max = 200)
        @Column(name = "JENKINS_LIBRARY", nullable = false, length = 200)
        String jenkinsLibrary,
        @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "ASOC_URL", nullable = false, length = 500)
        String asocUrl,
        @NotBlank @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "APPSCAN_CLIENT_LINUX_URL", nullable = false, length = 1000)
        String appScanClientLinuxUrl,
        @NotBlank @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "APPSCAN_CLIENT_WINDOWS_URL", nullable = false, length = 1000)
        String appScanClientWindowsUrl,
        @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE)
        @Column(name = "PROXY_HOST", length = 255)
        String proxyHost,
        @Min(1) @Max(65535)
        @Column(name = "PROXY_PORT")
        Integer proxyPort,
        @Size(max = 100)
        @Column(name = "PROXY_USER", length = 100)
        String proxyUser,
        @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE)
        @Column(name = "OIS_HOST", length = 255)
        String oisHost,
        @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "SONAR_SERVER_URL", nullable = false, length = 500)
        String sonarServerUrl,
        @NotBlank @Size(max = 200)
        @Column(name = "SONAR_INSTALLATION_NAME", nullable = false, length = 200)
        String sonarInstallationName,
        @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "NEXUS_IQ_SERVER_URL", nullable = false, length = 500)
        String nexusIqServerUrl,
        @NotBlank @Size(max = 200)
        @Column(name = "NEXUS_IQ_CREDENTIALS_ID", nullable = false, length = 200)
        String nexusIqCredentialsId,
        @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "NEXUS_SNAPSHOT_URL", length = 1000)
        String nexusSnapshotRepositoryUrl,
        @Size(max = 200)
        @Column(name = "NEXUS_SNAPSHOT_REPOSITORY_ID", length = 200)
        String nexusSnapshotRepositoryId,
        @Size(max = 1000) @Pattern(regexp = URL, message = URL_MESSAGE)
        @Column(name = "INFLUX_WRITE_URL", length = 1000)
        String influxWriteUrl,
        @Size(max = 200)
        @Column(name = "INFLUX_CREDENTIALS_ID", length = 200)
        String influxCredentialsId,
        @Size(max = 255)
        @Column(name = "IOS_BUILD_AGENT", length = 255)
        String iosBuildAgent) {

    static final String URL = "^(https?://\\S+)?$";
    static final String URL_MESSAGE = "must be an http or https URL";
    static final String HOST = "^[A-Za-z0-9.-]*$";
    static final String HOST_MESSAGE = "must be a host name";

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

    /**
     * The {@code platform} section of a pipeline's configuration. {@code environment} holds the variables the
     * library sets for its tools today, under the same names, so it can take them from here.
     */
    public Map<String, Object> toConfig() {
        Map<String, Object> environment = new LinkedHashMap<>();
        putIfSet(environment, "APPSCAN_SERVER_URL", asocUrl);
        putIfSet(environment, "APPSCAN_HOST", host(asocUrl));
        putIfSet(environment, "SA_LINUX_URL", appScanClientLinuxUrl);
        putIfSet(environment, "SA_WIN_URL", appScanClientWindowsUrl);
        putIfSet(environment, "PROXY_HOST", proxyHost);
        putIfSet(environment, "PROXY_PORT", proxyPort == null ? null : String.valueOf(proxyPort));
        putIfSet(environment, "PROXY_USER", proxyUser);
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

    /** The tool servers and credentials a project entry names; a service's own value written later wins. */
    public void writeProjectDefaults(ConfigTree config) {
        config.set("asoc.url", asocUrl)
                .set("influx.url", influxWriteUrl)
                .set("influx.credentialsId", influxCredentialsId)
                .set("tools.sonar.serverUrl", sonarServerUrl)
                .set("tools.sonar.installationName", sonarInstallationName)
                .set("tools.nexusIq.serverUrl", nexusIqServerUrl)
                .set("tools.nexusIq.credentialsId", nexusIqCredentialsId);
    }

    /** The AppScan calls go through the proxy only when both its host and port are known. */
    public void validate(ValidationProblems problems) {
        if (proxyHost != null && proxyPort == null) {
            problems.add("proxyPort", "is required with a proxy host");
        }
        if (proxyHost == null && proxyPort != null) {
            problems.add("proxyHost", "is required with a proxy port");
        }
    }

    private static void putIfSet(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
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
