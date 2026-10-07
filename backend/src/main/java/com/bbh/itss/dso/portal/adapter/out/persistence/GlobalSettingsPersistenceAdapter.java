package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;

@Component
class GlobalSettingsPersistenceAdapter implements GlobalSettingsRepositoryPort {

    private final GlobalSettingsJpaRepository repository;
    GlobalSettingsPersistenceAdapter(GlobalSettingsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<GlobalSettings> load() {
        return repository.findById(GlobalSettingsEntity.ID).map(GlobalSettingsEntity::toDomain);
    }

    @Override
    public GlobalSettings save(GlobalSettings settings) {
        GlobalSettingsEntity entity = repository.findById(GlobalSettingsEntity.ID).orElseGet(GlobalSettingsEntity::new);
        if (!entity.isNew() && entity.getVersion() != settings.version()) {
            throw staleVersion();
        }
        entity.apply(settings.values());
        return repository.saveAndFlush(entity).toDomain();
    }
}
