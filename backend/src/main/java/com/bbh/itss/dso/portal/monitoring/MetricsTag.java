package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.pipeline.Pipeline;

public record MetricsTag(String project, String env) {

    public static MetricsTag of(Pipeline pipeline) {
        return new MetricsTag(pipeline.influxProjectTag(), pipeline.influxEnv());
    }
}
