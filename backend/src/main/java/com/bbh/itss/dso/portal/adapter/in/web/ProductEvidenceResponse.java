package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.evidence.port.in.ProductEvidence;
import com.bbh.itss.dso.portal.domain.catalog.Product;

import java.util.List;

public record ProductEvidenceResponse(Long productId, String code, String name, String description, String ownerTeam,
                                      String contactEmail, List<ServiceEvidenceResponse> services,
                                      String metricsError) {

    static ProductEvidenceResponse from(ProductEvidence evidence) {
        Product product = evidence.product();
        return new ProductEvidenceResponse(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(),
                evidence.services().stream().map(ServiceEvidenceResponse::from).toList(), evidence.metricsError());
    }
}
