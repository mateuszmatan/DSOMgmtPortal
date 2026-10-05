package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.Product;

import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String description,
        String ownerTeam,
        String contactEmail,
        AppScanAccountDto appScan,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ServiceResponse> services) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(), AppScanAccountDto.from(product.appScanAccount()),
                product.version(), product.createdAt(), product.updatedAt(),
                product.services().stream().map(ServiceResponse::from).toList());
    }
}
