package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublishedConfigRepositoryPort;
import com.bbh.itss.dso.portal.domain.dsoconfig.PublishedConfig;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class PublishedConfigPersistenceAdapter implements PublishedConfigRepositoryPort {

    private final PublishedPipelineConfigJpaRepository repository;

    PublishedConfigPersistenceAdapter(PublishedPipelineConfigJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PublishedConfig> load(long pipelineId) {
        return repository.findById(pipelineId).map(entity ->
                new PublishedConfig(entity.getId(), entity.getConfigJson(), entity.getRenderedAt()));
    }

    @Override
    public void save(PublishedConfig config) {
        PublishedPipelineConfigEntity entity = repository.findById(config.pipelineId())
                .orElseGet(() -> new PublishedPipelineConfigEntity(config.pipelineId()));
        entity.publish(config.configJson(), config.renderedAt());
        if (entity.isNew()) {
            repository.save(entity);
        }
    }
}
