package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional
public class PipelineConfigPublisher implements PublishPipelineConfigsUseCase {

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final PublishedPipelineConfigRepository published;
    private final DsoConfigBuilder builder;
    private final JsonMapper json;

    public PipelineConfigPublisher(ProductRepositoryPort products, PipelineRepositoryPort pipelines,
                                   PublishedPipelineConfigRepository published, DsoConfigBuilder builder,
                                   JsonMapper json) {
        this.products = products;
        this.pipelines = pipelines;
        this.published = published;
        this.builder = builder;
        this.json = json;
    }

    @Override
    public void pipelineChanged(long pipelineId) {
        pipelines.load(pipelineId).ifPresent(pipeline -> products.load(pipeline.service().productId())
                .ifPresent(product -> publish(product, pipeline, Timestamps.now())));
    }

    @Override
    public void productChanged(long productId) {
        products.load(productId).ifPresent(product -> {
            Instant now = Timestamps.now();
            pipelines.findByProductId(productId).forEach(pipeline -> publish(product, pipeline, now));
        });
    }

    @Override
    public void settingsChanged() {
        publishAll();
    }

    @Override
    public int publishAll() {
        Map<Long, Product> byId = products.findAll().stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        List<Pipeline> all = pipelines.findAll();
        Instant now = Timestamps.now();
        all.forEach(pipeline -> publish(byId.get(pipeline.service().productId()), pipeline, now));
        return all.size();
    }

    private void publish(Product product, Pipeline pipeline, Instant now) {
        product.service(pipeline.service().serviceId()).ifPresent(service -> {
            PublishedPipelineConfig config = published.findById(pipeline.id())
                    .orElseGet(() -> new PublishedPipelineConfig(pipeline.id()));
            String rendered = json.writeValueAsString(builder.pipelineConfig(product, service, pipeline));
            if (config.publish(rendered, now) && config.isNew()) {
                published.save(config);
            }
        });
    }
}
