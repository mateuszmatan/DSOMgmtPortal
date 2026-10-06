package com.bbh.itss.dso.portal.application.settings;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException;

@UseCase
public class GlobalSettingsService implements ManageGlobalSettingsUseCase {

    private final GlobalSettingsRepositoryPort repository;

    public GlobalSettingsService(GlobalSettingsRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public GlobalSettings ensureExists() {
        return repository.load().orElseGet(() -> repository.save(GlobalSettings.bbhDefaults()));
    }

    @Override
    @ReadOnly
    public GlobalSettings current() {
        return load();
    }

    @Override
    public GlobalSettings update(Long expectedVersion, GlobalSettingsValues values) {
        return repository.save(load().change(expectedVersion, values));
    }

    private GlobalSettings load() {
        return repository.load().orElseThrow(MissingGlobalSettingsException::new);
    }
}
