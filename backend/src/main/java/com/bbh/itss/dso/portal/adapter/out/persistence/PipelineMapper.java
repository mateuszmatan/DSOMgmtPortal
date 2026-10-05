package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PipelineMapper {

    Pipeline toDomain(PipelineEntity entity) {
        ServiceEntity service = entity.service();
        ServiceRef ref = new ServiceRef(service.product().getId(), service.getId());
        List<PipelineKey> keys = entity.keys().stream().map(PipelineMapper::toDomain).toList();
        return Pipeline.restore(entity.getId(), ref, entity.type(), entity.settings().toDomain(), keys,
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    void copy(Pipeline pipeline, PipelineEntity entity) {
        entity.settings(PipelineSettingsEmbeddable.of(pipeline.settings()));
        pipeline.keys().stream().filter(key -> key.id() != null)
                .forEach(key -> entity.key(key.id()).orElseThrow()
                        .state(key.status(), key.revokedAt(), key.revokeReason()));
    }

    void addIssuedKeys(Pipeline pipeline, PipelineEntity entity) {
        List<PipelineKey> keys = pipeline.keys();
        for (int index = keys.size() - 1; index >= 0; index--) {
            PipelineKey key = keys.get(index);
            if (key.id() == null) {
                added(entity, key).state(key.status(), key.revokedAt(), key.revokeReason());
            }
        }
    }

    private static PipelineKeyEntity added(PipelineEntity pipeline, PipelineKey key) {
        PipelineKeyEntity entity = new PipelineKeyEntity(pipeline, key.value(), key.issuedAt());
        pipeline.addKey(entity);
        return entity;
    }

    private static PipelineKey toDomain(PipelineKeyEntity entity) {
        return new PipelineKey(entity.getId(), entity.value(), entity.status(), entity.issuedAt(), entity.revokedAt(),
                entity.revokeReason(), entity.lastUsedAt());
    }
}
