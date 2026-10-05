package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;

import java.util.List;

public record ProductCommand(Long version, ProductDetails details, AppScanAccount appScan, List<ServiceDraft> services) {

    public ProductCommand {
        services = services == null ? List.of() : List.copyOf(services);
    }
}
