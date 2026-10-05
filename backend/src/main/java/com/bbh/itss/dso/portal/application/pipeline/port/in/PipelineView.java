package com.bbh.itss.dso.portal.application.pipeline.port.in;

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

import java.util.Objects;

public record PipelineView(Product product, Service service, Pipeline pipeline, String jenkinsUrl) {

    public PipelineView {
        Objects.requireNonNull(product, "a pipeline view needs the product");
        Objects.requireNonNull(service, "a pipeline view needs the service");
        Objects.requireNonNull(pipeline, "a pipeline view needs the pipeline");
    }

    public static PipelineView of(Product product, Pipeline pipeline, String jenkinsUrl) {
        Service service = product.service(pipeline.service().serviceId()).orElseThrow(() -> new IllegalStateException(
                "pipeline " + pipeline.id() + " belongs to no service of product " + product.id()));
        return new PipelineView(product, service, pipeline, jenkinsUrl);
    }

    public String jenkinsJobUrl() {
        return pipeline.settings().jenkinsJobUrl(jenkinsUrl);
    }

    public String influxProjectTag() {
        return pipeline.type().influxProjectTag(metrics().influxProject());
    }

    public String influxEnv() {
        return metrics().influxEnv();
    }

    private MetricsSettings metrics() {
        return service.settings().metrics();
    }
}
