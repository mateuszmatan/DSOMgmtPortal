package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.catalog.Product;
import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.catalog.ServiceDefinition;
import com.bbh.itss.dso.portal.catalog.ServiceDefinitionRepository;
import com.bbh.itss.dso.portal.common.ConflictException;
import com.bbh.itss.dso.portal.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates pipelines and manages their keys. Key changes run under a row lock on the pipeline, so two
 * concurrent requests can never leave a pipeline with two active keys.
 */
@Service
@Transactional
public class PipelineService {

    private final PipelineRepository pipelines;
    private final PipelineKeyRepository keys;
    private final ServiceDefinitionRepository services;
    private final ProductRepository products;

    public PipelineService(PipelineRepository pipelines, PipelineKeyRepository keys,
                           ServiceDefinitionRepository services, ProductRepository products) {
        this.pipelines = pipelines;
        this.keys = keys;
        this.services = services;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ServicePipelines> listForProduct(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        Map<Long, List<PipelineResponse>> byService = pipelines.findByProductId(productId).stream()
                .map(PipelineResponse::summary)
                .collect(Collectors.groupingBy(PipelineResponse::serviceId));
        return product.getServices().stream()
                .map(service -> ServicePipelines.of(service, byService.getOrDefault(service.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineResponse get(Long id) {
        return PipelineResponse.withKeys(find(id));
    }

    /** The pipeline with its service, to render its configuration without counting that as a use of its key. */
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
        return PipelineResponse.withKeys(pipelines.saveAndFlush(new Pipeline(service, request.type(), request.settings())));
    }

    public PipelineResponse update(Long id, PipelineRequest request) {
        Pipeline pipeline = find(id);
        if (request.type() != pipeline.getType()) {
            throw new ConflictException("The type of a pipeline cannot change; add a new pipeline instead");
        }
        pipeline.configure(request.settings());
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    public void delete(Long id) {
        pipelines.delete(find(id));
    }

    public PipelineResponse revokeKey(Long id, String reason) {
        Pipeline pipeline = lock(id);
        pipeline.revokeActiveKey(reason);
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    public PipelineResponse issueKey(Long id) {
        Pipeline pipeline = lock(id);
        pipeline.issueKey();
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    /**
     * Finds the pipeline a DevSecOps job identifies itself with and records that the key was used.
     *
     * @throws NotFoundException   when no such key was ever issued
     * @throws KeyRevokedException when the key has been invalidated
     */
    public Pipeline resolveKey(String keyValue) {
        PipelineKey key = keys.findByValue(keyValue.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Unknown DevSecOps pipeline key"));
        if (!key.isActive()) {
            throw new KeyRevokedException(key);
        }
        key.markUsed();
        return key.getPipeline();
    }

    private Pipeline find(Long id) {
        return pipelines.findWithServiceById(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
    }

    private Pipeline lock(Long id) {
        pipelines.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
        return find(id);
    }
}
