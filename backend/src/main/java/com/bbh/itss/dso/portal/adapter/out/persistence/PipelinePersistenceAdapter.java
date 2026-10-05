package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
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

@Component
class PipelinePersistenceAdapter implements PipelineRepositoryPort, PipelineCountsPort {

    private final PipelineJpaRepository pipelines;
    private final PipelineKeyJpaRepository keys;
    private final ServiceJpaRepository services;
    private final PipelineMapper mapper;

    PipelinePersistenceAdapter(PipelineJpaRepository pipelines, PipelineKeyJpaRepository keys,
                               ServiceJpaRepository services, PipelineMapper mapper) {
        this.pipelines = pipelines;
        this.keys = keys;
        this.services = services;
        this.mapper = mapper;
    }

    @Override
    public Optional<Pipeline> load(long id) {
        return pipelines.findWithServiceById(id).map(mapper::toDomain);
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
        return pipelines.findByProductId(productId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Pipeline> findAll() {
        return pipelines.findAllWithService().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsForService(long serviceId, PipelineType type) {
        return pipelines.existsByServiceIdAndType(serviceId, type);
    }

    @Override
    public Pipeline save(Pipeline pipeline) {
        PipelineEntity entity = pipeline.id() == null ? created(pipeline) : existing(pipeline);
        mapper.copy(pipeline, entity);
        if (pipeline.id() != null) {
            pipelines.flush();
        }
        mapper.addIssuedKeys(pipeline, entity);
        return mapper.toDomain(pipelines.saveAndFlush(entity));
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
