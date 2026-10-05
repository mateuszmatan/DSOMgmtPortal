package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ProductHealth(Product product, int pipelineCount, RunResult overall,
                            Map<RunResult, Integer> statusCounts, Instant lastRunAt) {

    public ProductHealth {
        Objects.requireNonNull(product, "the health of a product names the product");
        statusCounts = statusCounts.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(statusCounts));
    }
}
