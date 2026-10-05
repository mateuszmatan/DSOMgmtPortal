package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringStatus;

public record MonitoringStatusResponse(boolean influxConfigured, boolean influxReachable, String influxError,
                                       boolean grafanaConfigured, String grafanaUrl) {

    static MonitoringStatusResponse from(MonitoringStatus status) {
        return new MonitoringStatusResponse(status.metricsConfigured(), status.metricsReachable(),
                status.metricsError(), status.dashboardsConfigured(), status.dashboardsUrl());
    }
}
