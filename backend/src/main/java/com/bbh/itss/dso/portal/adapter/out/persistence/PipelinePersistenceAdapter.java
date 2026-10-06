package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.IssuedKey;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class PipelinePersistenceAdapter implements PipelineRepositoryPort, PipelineCountsPort {

    private final PipelineJpaRepository pipelines;
    private final PipelineKeyJpaRepository keys;
    private final ServiceJpaRepository services;
    PipelinePersistenceAdapter(PipelineJpaRepository pipelines, PipelineKeyJpaRepository keys,
                               ServiceJpaRepository services) {
        this.pipelines = pipelines;
        this.keys = keys;
        this.services = services;
    }

    @Override
    public Optional<Pipeline> load(long id) {
        return pipelines.findWithServiceById(id).map(PipelineEntity::toDomain);
    }

    @Override
    public Optional<Pipeline> loadForUpdate(long id) {
        return pipelines.findForUpdate(id).flatMap(locked -> load(id));
    }

    @Override
    public Optional<IssuedKey> findKey(String keyValue) {
        return keys.findIssuedKey(keyValue).map(IssuedKeyRow::toDomain);
    }

    @Override
    public List<Pipeline> findByProductId(long productId) {
        return pipelines.findByProductId(productId).stream().map(PipelineEntity::toDomain).toList();
    }

    @Override
    public List<Pipeline> findAll() {
        return pipelines.findAllWithService().stream().map(PipelineEntity::toDomain).toList();
    }

    @Override
    public Set<MetricsTag> sharedMetricsTags() {
        return pipelines.metricsTags().stream()
                .collect(Collectors.groupingBy(
                        row -> MetricsTag.of((String) row[0], (String) row[1], (PipelineType) row[2]),
                        Collectors.mapping(row -> row[3], Collectors.toSet())))
                .entrySet().stream()
                .filter(services -> services.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean existsForService(long serviceId, PipelineType type) {
        return pipelines.existsByServiceIdAndType(serviceId, type);
    }

    @Override
    public Pipeline save(Pipeline pipeline) {
        PipelineEntity entity = pipeline.id() == null ? created(pipeline) : existing(pipeline);
        entity.apply(pipeline);
        if (pipeline.id() != null) {
            pipelines.flush();
        }
        entity.addIssuedKeys(pipeline);
        return pipelines.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(long id) {
        pipelines.findById(id).ifPresent(pipelines::delete);
    }

    @Override
    public boolean recordKeyUse(long keyId, Instant usedAt) {
        return keys.recordUse(keyId, usedAt) == 1;
    }

    @Override
    public Map<Long, Long> pipelinesPerProduct() {
        return Counts.perProduct(pipelines.countByProduct());
    }

    @Override
    public Map<Long, Long> activePipelinesPerProduct() {
        return Counts.perProduct(pipelines.countWithActiveKeyByProduct());
    }

    private PipelineEntity created(Pipeline pipeline) {
        long serviceId = pipeline.service().serviceId();
        ServiceEntity service = services.findWithProductById(serviceId)
                .orElseThrow(() -> NotFoundException.of("Service", serviceId));
        return new PipelineEntity(service, pipeline.type());
    }

    private PipelineEntity existing(Pipeline pipeline) {
        PipelineEntity entity = pipelines.findWithServiceById(pipeline.id()).orElseThrow(ConflictException::staleVersion);
        if (entity.getVersion() != pipeline.version()) {
            throw ConflictException.staleVersion();
        }
        return entity;
    }
}
