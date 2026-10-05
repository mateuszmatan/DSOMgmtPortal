package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineMonitoring;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.util.List;

public record PipelineMonitoringResponse(PipelineResponse pipeline, RunResult status, PipelineRunResponse lastRun,
                                         DoraSummaryResponse dora, List<PipelineRunResponse> recentRuns,
                                         GrafanaLinksResponse grafana, String metricsError) {

    static PipelineMonitoringResponse from(PipelineMonitoring monitoring) {
        return new PipelineMonitoringResponse(PipelineResponse.monitored(monitoring.pipeline()), monitoring.status(),
                PipelineRunResponse.from(monitoring.lastRun(), monitoring.pipeline()),
                DoraSummaryResponse.from(monitoring.dora()),
                monitoring.recentRuns().stream().map(run -> PipelineRunResponse.from(run, monitoring.pipeline()))
                        .toList(),
                GrafanaLinksResponse.from(monitoring.dashboards()), monitoring.metricsError());
    }
}
