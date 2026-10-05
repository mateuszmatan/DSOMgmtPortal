package com.bbh.itss.dso.portal.application.pipeline;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineCommand;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef;
import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
public class PipelineService implements ManagePipelinesUseCase, ManagePipelineKeysUseCase, QueryPipelinesUseCase {

    private final PipelineRepositoryPort pipelines;
    private final ProductRepositoryPort products;
    private final ManageGlobalSettingsUseCase settings;
    private final PublishPipelineConfigsUseCase publisher;
    private final KeyGenerator keys;
    private final Clock clock;

    public PipelineService(PipelineRepositoryPort pipelines, ProductRepositoryPort products,
                           ManageGlobalSettingsUseCase settings, PublishPipelineConfigsUseCase publisher,
                           KeyGenerator keys, Clock clock) {
        this.pipelines = pipelines;
        this.products = products;
        this.settings = settings;
        this.publisher = publisher;
        this.keys = keys;
        this.clock = clock;
    }

    @Override
    @ReadOnly
    public List<ServicePipelinesView> listForProduct(long productId) {
        Product product = products.load(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        String jenkinsUrl = jenkinsUrl();
        Map<Long, List<PipelineView>> byService = pipelines.findByProductId(productId).stream()
                .map(pipeline -> PipelineView.of(product, pipeline, jenkinsUrl))
                .collect(Collectors.groupingBy(view -> view.service().id()));
        return product.services().stream()
                .map(service -> new ServicePipelinesView(service, byService.getOrDefault(service.id(), List.of())))
                .toList();
    }

    @Override
    @ReadOnly
    public PipelineView get(long id) {
        return view(find(id));
    }

    @Override
    public PipelineView create(long serviceId, PipelineCommand command) {
        Product product = products.findByServiceId(serviceId)
                .orElseThrow(() -> NotFoundException.of("Service", serviceId));
        Service service = product.service(serviceId).orElseThrow(() -> NotFoundException.of("Service", serviceId));
        if (pipelines.existsForService(serviceId, command.type())) {
            throw new ConflictException("Service " + service.name() + " already has a " + command.type().variant()
                    + " pipeline");
        }
        Pipeline created = Pipeline.create(new ServiceRef(product.id(), serviceId), command.type(), command.settings(),
                keys, now());
        Pipeline saved = pipelines.save(created);
        publisher.pipelineChanged(saved.id());
        return PipelineView.of(product, saved, jenkinsUrl());
    }

    @Override
    public PipelineView update(long id, PipelineCommand command) {
        Pipeline pipeline = find(id);
        pipeline.reconfigure(command.type(), command.settings());
        Pipeline saved = pipelines.save(pipeline);
        publisher.pipelineChanged(saved.id());
        return view(saved);
    }

    @Override
    public void delete(long id) {
        find(id);
        pipelines.delete(id);
    }

    @Override
    public PipelineView issueKey(long pipelineId) {
        Pipeline pipeline = lock(pipelineId);
        pipeline.issueKey(keys, now());
        return view(pipelines.save(pipeline));
    }

    @Override
    public PipelineView revokeKey(long pipelineId, String reason) {
        Pipeline pipeline = lock(pipelineId);
        pipeline.revokeActiveKey(reason, now());
        return view(pipelines.save(pipeline));
    }

    @Override
    public PipelineView resolveKey(String keyValue) {
        Pipeline pipeline = pipelines.findByKey(PipelineKey.normalize(keyValue))
                .orElseThrow(() -> new NotFoundException(Pipeline.UNKNOWN_KEY));
        Instant now = now();
        PipelineKey used = pipeline.authorize(keyValue, now);
        pipelines.recordKeyUse(used.id(), now);
        return view(pipeline);
    }

    private PipelineView view(Pipeline pipeline) {
        Product product = products.load(pipeline.service().productId())
                .orElseThrow(() -> NotFoundException.of("Product", pipeline.service().productId()));
        return PipelineView.of(product, pipeline, jenkinsUrl());
    }

    private Pipeline find(long id) {
        return pipelines.load(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
    }

    private Pipeline lock(long id) {
        return pipelines.loadForUpdate(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
    }

    private String jenkinsUrl() {
        return settings.current().jenkinsUrl();
    }

    private Instant now() {
        return Timestamps.now(clock);
    }
}
