package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductMonitoring;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.util.List;

public record ProductMonitoringResponse(Long productId, String code, String name, String description,
                                        String ownerTeam, RunResult overall, List<PipelineHealthResponse> pipelines,
                                        String metricsError) {

    static ProductMonitoringResponse from(ProductMonitoring monitoring) {
        Product product = monitoring.product();
        return new ProductMonitoringResponse(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), monitoring.overall(),
                monitoring.pipelines().stream().map(PipelineHealthResponse::from).toList(), monitoring.metricsError());
    }
}
