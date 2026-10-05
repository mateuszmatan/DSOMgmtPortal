package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class GlobalSettingsPersistenceAdapter implements GlobalSettingsRepositoryPort {

    private final GlobalSettingsJpaRepository repository;
    private final GlobalSettingsMapper mapper;

    GlobalSettingsPersistenceAdapter(GlobalSettingsJpaRepository repository, GlobalSettingsMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<GlobalSettings> load() {
        return repository.findById(GlobalSettingsEntity.ID).map(mapper::toDomain);
    }

    @Override
    public GlobalSettings save(GlobalSettings settings) {
        GlobalSettingsEntity entity = repository.findById(GlobalSettingsEntity.ID).orElseGet(GlobalSettingsEntity::new);
        if (!entity.isNew() && entity.getVersion() != settings.version()) {
            throw ConflictException.staleVersion();
        }
        mapper.copy(settings.values(), entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }
}
