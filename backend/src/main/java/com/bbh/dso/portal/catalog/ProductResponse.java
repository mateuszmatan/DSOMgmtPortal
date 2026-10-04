package com.bbh.dso.portal.catalog;

import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String description,
        String ownerTeam,
        String contactEmail,
        AppScanAccount appScan,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ServiceResponse> services) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getCode(), product.getName(), product.getDescription(),
                product.getOwnerTeam(), product.getContactEmail(), product.getAppScanAccount(), product.getVersion(),
                product.getCreatedAt(), product.getUpdatedAt(),
                product.getServices().stream().map(ServiceResponse::from).toList());
    }
}
