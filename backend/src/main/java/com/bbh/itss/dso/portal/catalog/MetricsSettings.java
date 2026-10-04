package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record MetricsSettings(
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "METRICS_ENABLED", nullable = false)
        Boolean enabled,
        @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
        @Column(name = "INFLUX_PROJECT", nullable = false, length = 200)
        String influxProject,
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
        @Column(name = "INFLUX_ENV", nullable = false, length = 50)
        String influxEnv) implements ConfigSection {

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
