package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

public record MetricsTag(String project, String env) {

    public static MetricsTag of(Service service, Pipeline pipeline) {
        MetricsSettings metrics = service.settings().metrics();
        return new MetricsTag(pipeline.type().influxProjectTag(metrics.influxProject()), metrics.influxEnv());
    }
}
