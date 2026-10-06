package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigSection;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

public record MetricsSettings(Boolean enabled, String influxProject, String influxEnv, String influxUrl,
                              String influxCredentialsId) implements ConfigSection {

    public static final String DEFAULT_ENV = "test";
    public static final MetricsSettings DEFAULTS = new MetricsSettings(true, null, null, null, null);

    public MetricsSettings {
        enabled = !Boolean.FALSE.equals(enabled);
        influxProject = Text.trimToNull(influxProject);
        influxEnv = Text.orDefault(influxEnv, DEFAULT_ENV);
        influxUrl = Text.trimToNull(influxUrl);
        influxCredentialsId = Text.trimToNull(influxCredentialsId);
    }

    public static MetricsSettings of(boolean enabled, String influxProject, String influxEnv) {
        return new MetricsSettings(enabled, influxProject, influxEnv, null, null);
    }

    public MetricsSettings withDefaultProject(String productCode, String serviceName) {
        return influxProject != null ? this
                : new MetricsSettings(enabled, productCode + "-" + serviceName, influxEnv, influxUrl, influxCredentialsId);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("influx.enabled", enabled).set("influx.url", influxUrl)
                .set("influx.credentialsId", influxCredentialsId).set("influx.project", influxProject)
                .set("influx.env", influxEnv);
    }
}
