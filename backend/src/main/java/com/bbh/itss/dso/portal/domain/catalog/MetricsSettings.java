package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record MetricsSettings(Boolean enabled, String influxProject, String influxEnv, String influxUrl,
                              String influxCredentialsId) {

    public static final String DEFAULT_ENV = "test";
    public static final MetricsSettings DEFAULTS = new MetricsSettings(true, null, null, null, null);

    public MetricsSettings {
        enabled = !Boolean.FALSE.equals(enabled);
        influxProject = trimToNull(influxProject);
        influxEnv = defaultIfBlank(trim(influxEnv), DEFAULT_ENV);
        influxUrl = trimToNull(influxUrl);
        influxCredentialsId = trimToNull(influxCredentialsId);
    }

    public static MetricsSettings of(boolean enabled, String influxProject, String influxEnv) {
        return new MetricsSettings(enabled, influxProject, influxEnv, null, null);
    }

    public MetricsSettings withDefaultProject(String productCode, String serviceName) {
        return influxProject != null ? this
                : new MetricsSettings(enabled, productCode + "-" + serviceName, influxEnv, influxUrl, influxCredentialsId);
    }

    public void writeTo(ConfigTree config) {
        config.set("influx.enabled", enabled).set("influx.url", influxUrl)
                .set("influx.credentialsId", influxCredentialsId).set("influx.project", influxProject)
                .set("influx.env", influxEnv);
    }
}
