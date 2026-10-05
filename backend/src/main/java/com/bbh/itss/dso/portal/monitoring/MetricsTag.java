package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;

public record MetricsTag(String project, String env) {

    public static MetricsTag of(PipelineView view) {
        return new MetricsTag(view.influxProjectTag(), view.influxEnv());
    }
}
