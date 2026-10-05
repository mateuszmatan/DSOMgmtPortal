package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record MetricsSettingsEmbeddable(
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "METRICS_ENABLED", nullable = false) Boolean enabled,
        @Column(name = "INFLUX_PROJECT", nullable = false, length = 200) String influxProject,
        @Column(name = "INFLUX_ENV", nullable = false, length = 50) String influxEnv) {

    static MetricsSettingsEmbeddable of(MetricsSettings metrics) {
        return new MetricsSettingsEmbeddable(metrics.enabled(), metrics.influxProject(), metrics.influxEnv());
    }

    MetricsSettingsEmbeddable withInfluxProject(String project) {
        return new MetricsSettingsEmbeddable(enabled, project, influxEnv);
    }

    MetricsSettings toDomain() {
        return new MetricsSettings(enabled, influxProject, influxEnv);
    }
}
