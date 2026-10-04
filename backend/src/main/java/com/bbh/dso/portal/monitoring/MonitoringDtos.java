package com.bbh.dso.portal.monitoring;

import com.bbh.dso.portal.pipeline.PipelineResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Responses of the monitoring API.
 */
public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    public record MonitoringStatus(boolean influxConfigured, boolean influxReachable, String influxError,
                                   boolean grafanaConfigured, String grafanaUrl) {
    }

    public record ProductHealth(Long productId, String code, String name, String ownerTeam, int serviceCount,
                                int pipelineCount, RunResult overall, Map<RunResult, Integer> statusCounts,
                                Instant lastRunAt) {
    }

    public record Overview(List<ProductHealth> products, String metricsError) {
    }

    public record PipelineHealth(PipelineResponse pipeline, RunResult status, PipelineRun lastRun) {
    }

    public record ProductMonitoring(Long productId, String code, String name, String description, String ownerTeam,
                                    RunResult overall, List<PipelineHealth> pipelines, String metricsError) {
    }

    public record GrafanaPanel(int id, String title, int width, String url) {
    }

    public record GrafanaLinks(String dashboardUrl, List<GrafanaPanel> panels) {
    }

    public record PipelineMonitoring(PipelineResponse pipeline, RunResult status, PipelineRun lastRun,
                                     DoraSummary dora, List<PipelineRun> recentRuns, GrafanaLinks grafana,
                                     String metricsError) {
    }
}
