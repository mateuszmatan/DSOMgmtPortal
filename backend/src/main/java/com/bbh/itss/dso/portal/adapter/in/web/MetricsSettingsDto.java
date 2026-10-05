package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MetricsSettingsDto(
        Boolean enabled,
        @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
        String influxProject,
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, '.', '-' and '_'")
        String influxEnv) {

    public MetricsSettingsDto {
        enabled = !Boolean.FALSE.equals(enabled);
        influxProject = Text.trimToNull(influxProject);
        influxEnv = Text.orDefault(influxEnv, MetricsSettings.DEFAULT_ENV);
    }

    static MetricsSettingsDto from(MetricsSettings source) {
        return new MetricsSettingsDto(source.enabled(), source.influxProject(), source.influxEnv());
    }

    MetricsSettings toDomain() {
        return new MetricsSettings(enabled, influxProject, influxEnv);
    }
}
