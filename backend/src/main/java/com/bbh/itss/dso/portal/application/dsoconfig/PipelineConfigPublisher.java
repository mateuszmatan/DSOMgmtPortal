package com.bbh.itss.dso.portal.application.dsoconfig;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.ReadPublishedConfigUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublicationLockPort;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@UseCase
public class PipelineConfigPublisher implements PublishPipelineConfigsUseCase, ReadPublishedConfigUseCase {

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final GlobalSettingsRepositoryPort settings;
    private final PublishedConfigRepositoryPort published;
    private final ConfigSerializerPort serializer;
    private final PublicationLockPort lock;
    private final Clock clock;
    private volatile Instant republishedAt;

    public PipelineConfigPublisher(ProductRepositoryPort products, PipelineRepositoryPort pipelines,
                                   GlobalSettingsRepositoryPort settings, PublishedConfigRepositoryPort published,
                                   ConfigSerializerPort serializer, PublicationLockPort lock, Clock clock) {
        this.products = products;
        this.pipelines = pipelines;
        this.settings = settings;
        this.published = published;
        this.serializer = serializer;
        this.lock = lock;
        this.clock = clock;
    }

    @Override
    public void lockConfigurations() {
        lock.lock();
    }

    @Override
    public void pipelineChanged(long pipelineId) {
        lockConfigurations();
        pipelines.load(pipelineId).ifPresent(pipeline -> products.load(pipeline.service().productId())
                .ifPresent(product -> publish(List.of(pipeline), Map.of(product.id(), product), false)));
    }

    @Override
    public void productChanged(long productId) {
        lockConfigurations();
        products.load(productId).ifPresent(product ->
                publish(pipelines.findByProductId(productId), Map.of(product.id(), product), false));
    }

    @Override
    public void settingsChanged() {
        publishEvery(false);
    }

    @Override
    public int publishAll() {
        Instant started = Timestamps.now(clock);
        int count = publishEvery(true);
        republishedAt = started;
        return count;
    }

    @Override
    @ReadOnly
    public Optional<Map<String, Object>> currentConfig(long pipelineId) {
        Instant since = republishedAt;
        if (since == null) {
            return Optional.empty();
        }
        return published.load(pipelineId).filter(config -> config.renderedSince(since))
                .map(config -> serializer.fromJson(config.configJson()));
    }

    private int publishEvery(boolean force) {
        lockConfigurations();
        Map<Long, Product> byId = products.findAll().stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        List<Pipeline> all = pipelines.findAll();
        publish(all, byId, force);
        return all.size();
    }

    private void publish(List<Pipeline> changed, Map<Long, Product> productsById, boolean force) {
        if (changed.isEmpty()) {
            return;
        }
        DsoConfigBuilder builder = new DsoConfigBuilder(settings.load().map(GlobalSettings::values)
                .orElseThrow(MissingGlobalSettingsException::new));
        Instant now = Timestamps.now(clock);
        for (Pipeline pipeline : changed) {
            Product product = productsById.get(pipeline.service().productId());
            if (product != null) {
                publish(builder, product, pipeline, now, force);
            }
        }
    }

    private void publish(DsoConfigBuilder builder, Product product, Pipeline pipeline, Instant now, boolean force) {
        product.service(pipeline.service().serviceId()).ifPresent(service -> {
            String rendered = serializer.toJson(builder.pipelineConfig(product, service, pipeline));
            boolean unchanged = published.load(pipeline.id()).map(current -> current.holds(rendered)).orElse(false);
            if (force || !unchanged) {
                published.save(new PublishedConfig(pipeline.id(), rendered, now));
            }
        });
    }
}
