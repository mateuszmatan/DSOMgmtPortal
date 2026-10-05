package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductHealth;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;
import java.util.Map;

public record ProductHealthResponse(Long productId, String code, String name, String ownerTeam, int serviceCount,
                                    int pipelineCount, RunResult overall, Map<RunResult, Integer> statusCounts,
                                    Instant lastRunAt) {

    static ProductHealthResponse from(ProductHealth health) {
        Product product = health.product();
        return new ProductHealthResponse(product.id(), product.code(), product.name(), product.ownerTeam(),
                product.services().size(), health.pipelineCount(), health.overall(), health.statusCounts(),
                health.lastRunAt());
    }
}
