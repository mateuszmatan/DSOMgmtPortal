package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;

public record ProductDetailsView(long id, String code, String name, String ownerTeam, String contactEmail,
                                 Long departmentId, long version) {

    public static ProductDetailsView of(Product product) {
        return new ProductDetailsView(product.id(), product.code(), product.name(), product.ownerTeam(),
                product.contactEmail(), product.departmentId(), product.version());
    }
}
