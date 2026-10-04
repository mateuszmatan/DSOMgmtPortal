package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.pipeline.Pipeline;

/**
 * The InfluxDB tags that identify one pipeline's points: {@code project} and {@code env}.
 */
public record MetricsTag(String project, String env) {

    public static MetricsTag of(Pipeline pipeline) {
        return new MetricsTag(pipeline.influxProjectTag(), pipeline.influxEnv());
    }
}
