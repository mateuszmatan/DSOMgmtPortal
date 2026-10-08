package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;

public record ProductDetailsCommand(Long version, String name, Long departmentId, String ownerTeam,
                                    String contactEmail) {

    public ProductDetails applyTo(ProductDetails current) {
        return new ProductDetails(current.code(), name, current.description(), ownerTeam, contactEmail, departmentId);
    }
}
