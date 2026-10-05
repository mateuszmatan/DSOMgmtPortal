package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;

import java.util.List;

public record ProductCommand(Long version, ProductDetails details, AppScanAccount appScan, List<ServiceCommand> services) {

    public ProductCommand {
        services = services == null ? List.of() : List.copyOf(services);
    }

    public static ProductCommand unversioned(ProductDetails details, AppScanAccount appScan,
                                             List<ServiceCommand> services) {
        return new ProductCommand(null, details, appScan, services);
    }

    public List<ServiceDraft> drafts() {
        return services.stream().map(ServiceCommand::toDraft).toList();
    }
}
