package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;

import java.time.Instant;

public record ProductSummaryResponse(
        Long id,
        String code,
        String name,
        String description,
        String ownerTeam,
        long serviceCount,
        long pipelineCount,
        long activePipelineCount,
        Instant updatedAt) {

    static ProductSummaryResponse from(ProductSummaryView view) {
        return new ProductSummaryResponse(view.id(), view.code(), view.name(), view.description(), view.ownerTeam(),
                view.serviceCount(), view.pipelineCount(), view.activePipelineCount(), view.updatedAt());
    }
}
