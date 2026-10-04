package com.bbh.itss.dso.portal.catalog;

import java.time.Instant;

public record ProductSummary(
        Long id,
        String code,
        String name,
        String description,
        String ownerTeam,
        long serviceCount,
        long pipelineCount,
        long activePipelineCount,
        Instant updatedAt) {
}
