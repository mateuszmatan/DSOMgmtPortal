package com.bbh.itss.dso.portal.application.dsoconfig;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublishedConfigRepositoryPort;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.dsoconfig.DsoConfigBuilder;
import com.bbh.itss.dso.portal.domain.dsoconfig.PublishedConfig;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@UseCase
public class PipelineConfigPublisher implements PublishPipelineConfigsUseCase {

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final GlobalSettingsRepositoryPort settings;
    private final PublishedConfigRepositoryPort published;
    private final ConfigSerializerPort serializer;
    private final Clock clock;

    public PipelineConfigPublisher(ProductRepositoryPort products, PipelineRepositoryPort pipelines,
                                   GlobalSettingsRepositoryPort settings, PublishedConfigRepositoryPort published,
                                   ConfigSerializerPort serializer, Clock clock) {
        this.products = products;
        this.pipelines = pipelines;
        this.settings = settings;
        this.published = published;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    public void pipelineChanged(long pipelineId) {
        pipelines.load(pipelineId).ifPresent(pipeline -> products.load(pipeline.service().productId())
                .ifPresent(product -> publish(List.of(pipeline), Map.of(product.id(), product))));
    }

    @Override
    public void productChanged(long productId) {
        products.load(productId).ifPresent(product ->
                publish(pipelines.findByProductId(productId), Map.of(product.id(), product)));
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
        publish(all, byId);
        return all.size();
    }

    private void publish(List<Pipeline> changed, Map<Long, Product> productsById) {
        if (changed.isEmpty()) {
            return;
        }
        DsoConfigBuilder builder = new DsoConfigBuilder(settings.load().map(GlobalSettings::values)
                .orElseThrow(MissingGlobalSettingsException::new));
        Instant now = Timestamps.now(clock);
        for (Pipeline pipeline : changed) {
            Product product = productsById.get(pipeline.service().productId());
            if (product != null) {
                publish(builder, product, pipeline, now);
            }
        }
    }

    private void publish(DsoConfigBuilder builder, Product product, Pipeline pipeline, Instant now) {
        product.service(pipeline.service().serviceId()).ifPresent(service -> {
            String rendered = serializer.toJson(builder.pipelineConfig(product, service, pipeline));
            boolean unchanged = published.load(pipeline.id()).map(current -> current.holds(rendered)).orElse(false);
            if (!unchanged) {
                published.save(new PublishedConfig(pipeline.id(), rendered, now));
            }
        });
    }
}
