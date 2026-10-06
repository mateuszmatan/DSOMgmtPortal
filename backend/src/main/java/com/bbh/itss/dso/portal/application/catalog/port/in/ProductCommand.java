package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.util.List;

public record ProductCommand(Long version, ProductDetails details, AppScanAccount appScan, List<ServiceDraft> services,
                             PipelineType pipelineType) {

    public ProductCommand {
        services = services == null ? List.of() : List.copyOf(services);
    }

    public ProductCommand(Long version, ProductDetails details, AppScanAccount appScan, List<ServiceDraft> services) {
        this(version, details, appScan, services, null);
    }
}
