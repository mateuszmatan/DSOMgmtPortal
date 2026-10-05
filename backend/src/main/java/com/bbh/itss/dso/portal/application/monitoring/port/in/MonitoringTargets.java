package com.bbh.itss.dso.portal.application.monitoring.port.in;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;

import java.util.List;
import java.util.Objects;

public record MonitoringTargets(List<Product> products, List<PipelineView> pipelines, PlatformSettings platform) {

    public MonitoringTargets {
        products = List.copyOf(products);
        pipelines = List.copyOf(pipelines);
        Objects.requireNonNull(platform, "monitoring targets carry the platform settings their links are built on");
    }

    public Product product() {
        if (products.size() != 1) {
            throw new IllegalStateException("these monitoring targets are not those of one product");
        }
        return products.getFirst();
    }

    public PipelineView pipeline() {
        if (pipelines.size() != 1) {
            throw new IllegalStateException("these monitoring targets are not those of one pipeline");
        }
        return pipelines.getFirst();
    }
}
