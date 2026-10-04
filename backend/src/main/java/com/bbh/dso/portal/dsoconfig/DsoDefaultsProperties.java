package com.bbh.dso.portal.dsoconfig;

import com.bbh.dso.portal.catalog.ConfigSection;
import com.bbh.dso.portal.catalog.ConfigTree;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * BBH-wide values written into every generated configuration first: tool server URLs and the Jenkins
 * credential IDs shared by all products. A service can override any of them in its additional YAML.
 */
@ConfigurationProperties("dso.defaults")
public record DsoDefaultsProperties(
        @DefaultValue("https://bbh.cloud.appscan.com") String asocUrl,
        @DefaultValue("https://tools.bbh.com/sonar") String sonarServerUrl,
        @DefaultValue("https://tools.bbh.com/IQ") String nexusIqServerUrl,
        @DefaultValue("nexusiqP") String nexusIqCredentialsId,
        @DefaultValue("http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s")
        String influxWriteUrl,
        @DefaultValue("influxdb-token") String influxCredentialsId) implements ConfigSection {

    @Override
    public void writeTo(ConfigTree config) {
        config.set("asoc.url", asocUrl)
                .set("influx.url", influxWriteUrl)
                .set("influx.credentialsId", influxCredentialsId)
                .set("tools.sonar.serverUrl", sonarServerUrl)
                .set("tools.nexusIq.serverUrl", nexusIqServerUrl)
                .set("tools.nexusIq.credentialsId", nexusIqCredentialsId);
    }
}
