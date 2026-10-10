package com.bbh.itss.dso.portal.application.evidence.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record ProductEvidence(Product product, List<ServiceEvidence> services, String metricsError) {

    public ProductEvidence {
        requireNonNull(product, "evidence belongs to a product");
        services = List.copyOf(services);
    }
}
