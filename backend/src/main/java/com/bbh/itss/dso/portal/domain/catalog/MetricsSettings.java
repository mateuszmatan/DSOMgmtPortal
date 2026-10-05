package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigSection;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

public record MetricsSettings(Boolean enabled, String influxProject, String influxEnv) implements ConfigSection {

    public static final String DEFAULT_ENV = "test";
    public static final MetricsSettings DEFAULTS = new MetricsSettings(true, null, null);

    public MetricsSettings {
        enabled = !Boolean.FALSE.equals(enabled);
        influxProject = Text.trimToNull(influxProject);
        influxEnv = Text.orDefault(influxEnv, DEFAULT_ENV);
    }

    public MetricsSettings withDefaultProject(String productCode, String serviceName) {
        return influxProject != null ? this : new MetricsSettings(enabled, productCode + "-" + serviceName, influxEnv);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("influx.enabled", enabled).set("influx.project", influxProject).set("influx.env", influxEnv);
    }
}
