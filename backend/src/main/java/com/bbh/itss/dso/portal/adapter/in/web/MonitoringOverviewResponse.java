package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringOverview;

import java.util.List;

public record MonitoringOverviewResponse(List<ProductHealthResponse> products, String metricsError) {

    static MonitoringOverviewResponse from(MonitoringOverview overview) {
        return new MonitoringOverviewResponse(overview.products().stream().map(ProductHealthResponse::from).toList(),
                overview.metricsError());
    }
}
