package com.bbh.dso.portal.dsoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * BBH-wide values written into every generated configuration: tool server URLs and the Jenkins credential
 * IDs shared by all products. A service can still override any of them in its additional configuration.
 */
@ConfigurationProperties("dso.defaults")
public record DsoDefaultsProperties(
        @DefaultValue("https://bbh.cloud.appscan.com") String asocUrl,
        @DefaultValue("https://tools.bbh.com/sonar") String sonarServerUrl,
        @DefaultValue("https://tools.bbh.com/IQ") String nexusIqServerUrl,
        @DefaultValue("nexusiqP") String nexusIqCredentialsId,
        @DefaultValue("http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s")
        String influxWriteUrl,
        @DefaultValue("influxdb-token") String influxCredentialsId) {
}
