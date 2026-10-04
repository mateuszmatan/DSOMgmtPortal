package com.bbh.dso.portal.pipeline;

import com.bbh.dso.portal.catalog.Product;
import com.bbh.dso.portal.catalog.ProductRepository;
import com.bbh.dso.portal.catalog.ServiceDefinition;
import com.bbh.dso.portal.catalog.ServiceDefinitionRepository;
import com.bbh.dso.portal.common.ConflictException;
import com.bbh.dso.portal.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates pipelines and manages their keys. Every key change runs under a row lock on the pipeline, so a
 * pipeline never ends up with two active keys.
 */
@Service
@Transactional
public class PipelineService {

    static final String REPLACED_REASON = "Replaced by a new key";

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
                .map(s -> new ServicePipelines(s.getId(), s.getName(), s.getDescription(), s.getBuildTool(),
                        s.getDeployTarget(), byService.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineResponse get(Long id) {
        return PipelineResponse.withKeys(findWithService(id));
    }

    public PipelineResponse create(Long serviceId, PipelineRequest request) {
        ServiceDefinition service = services.findById(serviceId)
                .orElseThrow(() -> NotFoundException.of("Service", serviceId));
        if (pipelines.existsByServiceIdAndType(serviceId, request.type())) {
            throw new ConflictException("Service " + service.getName() + " already has a "
                    + request.type().variant() + " pipeline");
        }
        Pipeline pipeline = new Pipeline(service, request.type());
        apply(pipeline, request);
        pipeline.addKey(PipelineKey.issue(pipeline));
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    public PipelineResponse update(Long id, PipelineRequest request) {
        Pipeline pipeline = findWithService(id);
        if (request.type() != pipeline.getType()) {
            throw new ConflictException("The type of a pipeline cannot change; add a new pipeline instead");
        }
        apply(pipeline, request);
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    public void delete(Long id) {
        pipelines.delete(findWithService(id));
    }

    /**
     * Invalidates the active key. From then on the portal refuses the pipeline's configuration.
     */
    public PipelineResponse revokeKey(Long id, String reason) {
        Pipeline pipeline = lock(id);
        PipelineKey active = pipeline.activeKey()
                .orElseThrow(() -> new ConflictException("The pipeline has no active key to invalidate"));
        active.revoke(reason.trim());
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    /**
     * Issues a new key, invalidating the active one if there is one. The Jenkins job must then use the new key.
     */
    public PipelineResponse issueKey(Long id) {
        Pipeline pipeline = lock(id);
        pipeline.activeKey().ifPresent(active -> active.revoke(REPLACED_REASON));
        // The revocation is flushed first so the database never sees two active keys at once.
        pipelines.saveAndFlush(pipeline);
        pipeline.addKey(PipelineKey.issue(pipeline));
        return PipelineResponse.withKeys(pipelines.saveAndFlush(pipeline));
    }

    /**
     * Finds the pipeline a DevSecOps job identifies itself with and records that the key was used.
     *
     * @throws NotFoundException   when no such key was ever issued
     * @throws KeyRevokedException when the key has been invalidated
     */
    public Pipeline resolveKey(String keyValue) {
        PipelineKey key = keys.findByValue(keyValue.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Unknown DevSecOps pipeline key"));
        if (!key.isActive()) {
            throw new KeyRevokedException("The DevSecOps pipeline key was invalidated on " + key.getRevokedAt()
                    + (key.getRevokeReason() == null ? "" : ": " + key.getRevokeReason()));
        }
        key.markUsed();
        return key.getPipeline();
    }

    private Pipeline findWithService(Long id) {
        return pipelines.findWithServiceById(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
    }

    private Pipeline lock(Long id) {
        pipelines.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Pipeline", id));
        return findWithService(id);
    }

    private static void apply(Pipeline pipeline, PipelineRequest request) {
        pipeline.setAgentLabels(request.agentLabels().stream().map(String::trim).distinct()
                .collect(Collectors.joining(",")));
        pipeline.setExtendedPipelineJob(request.type() == PipelineType.SECURITY ? trimToNull(request.extendedPipelineJob()) : null);
        pipeline.setDescription(trimToNull(request.description()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
