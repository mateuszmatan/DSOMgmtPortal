package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.catalog.Product;
import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.catalog.ServiceDefinition;
import com.bbh.itss.dso.portal.catalog.ServiceDefinitionRepository;
import com.bbh.itss.dso.portal.common.ConflictException;
import com.bbh.itss.dso.portal.common.NotFoundException;
import com.bbh.itss.dso.portal.settings.GlobalSettingsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PipelineService {

    private final PipelineRepository pipelines;
    private final PipelineKeyRepository keys;
    private final ServiceDefinitionRepository services;
    private final ProductRepository products;
    private final GlobalSettingsService settings;
    private final ApplicationEventPublisher events;

    public PipelineService(PipelineRepository pipelines, PipelineKeyRepository keys,
                           ServiceDefinitionRepository services, ProductRepository products,
                           GlobalSettingsService settings, ApplicationEventPublisher events) {
        this.pipelines = pipelines;
        this.keys = keys;
        this.services = services;
        this.products = products;
        this.settings = settings;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ServicePipelines> listForProduct(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        String jenkinsUrl = settings.jenkinsUrl();
        Map<Long, List<PipelineResponse>> byService = pipelines.findByProductId(productId).stream()
                .map(pipeline -> PipelineResponse.summary(pipeline, jenkinsUrl))
                .collect(Collectors.groupingBy(PipelineResponse::serviceId));
        return product.getServices().stream()
                .map(service -> ServicePipelines.of(service, byService.getOrDefault(service.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineResponse get(Long id) {
        return withKeys(find(id));
    }

    @Transactional(readOnly = true)
    public Pipeline pipeline(Long id) {
        return find(id);
    }

    public PipelineResponse create(Long serviceId, PipelineRequest request) {
        ServiceDefinition service = services.findById(serviceId)
                .orElseThrow(() -> NotFoundException.of("Service", serviceId));
        if (pipelines.existsByServiceIdAndType(serviceId, request.type())) {
            throw new ConflictException("Service " + service.getName() + " already has a "
                    + request.type().variant() + " pipeline");
        }
        return changed(pipelines.saveAndFlush(new Pipeline(service, request.type(), request.settings())));
    }

    public PipelineResponse update(Long id, PipelineRequest request) {
        Pipeline pipeline = find(id);
        if (request.type() != pipeline.getType()) {
            throw new ConflictException("The type of a pipeline cannot change; add a new pipeline instead");
        }
        pipeline.configure(request.settings());
        return changed(pipelines.saveAndFlush(pipeline));
    }

    public void delete(Long id) {
        pipelines.delete(find(id));
    }

    public PipelineResponse revokeKey(Long id, String reason) {
        Pipeline pipeline = lock(id);
        pipeline.revokeActiveKey(reason);
        return withKeys(pipelines.saveAndFlush(pipeline));
    }

    public PipelineResponse issueKey(Long id) {
        Pipeline pipeline = lock(id);
        pipeline.issueKey();
        return withKeys(pipelines.saveAndFlush(pipeline));
    }

    public Pipeline resolveKey(String keyValue) {
        PipelineKey key = keys.findByValue(keyValue.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Unknown DevSecOps pipeline key"));
        if (!key.isActive()) {
            throw new KeyRevokedException(key);
        }
        key.markUsed();
        return key.getPipeline();
    }

    private PipelineResponse changed(Pipeline pipeline) {
        events.publishEvent(new PipelineChanged(pipeline.getId()));
        return withKeys(pipeline);
    }

    private PipelineResponse withKeys(Pipeline pipeline) {
        return PipelineResponse.withKeys(pipeline, settings.jenkinsUrl());
    }

    private Pipeline find(Long id) {
        return pipelines.findWithServiceById(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
    }

    private Pipeline lock(Long id) {
        pipelines.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
        return find(id);
    }
}
