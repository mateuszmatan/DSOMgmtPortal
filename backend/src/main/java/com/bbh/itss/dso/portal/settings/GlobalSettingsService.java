package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.common.ValidationProblems;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GlobalSettingsService {

    private final GlobalSettingsRepository repository;
    private final ApplicationEventPublisher events;

    public GlobalSettingsService(GlobalSettingsRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public GlobalSettings ensureExists() {
        return repository.findById(GlobalSettings.ID)
                .orElseGet(() -> repository.saveAndFlush(new GlobalSettings(GlobalSettingsValues.bbhDefaults())));
    }

    @Transactional(readOnly = true)
    public GlobalSettingsValues values() {
        return load().values();
    }

    @Transactional(readOnly = true)
    public String jenkinsUrl() {
        return load().platform().jenkinsUrl();
    }

    @Transactional(readOnly = true)
    public GlobalSettingsResponse get() {
        return GlobalSettingsResponse.from(load());
    }

    public GlobalSettingsResponse update(GlobalSettingsRequest request) {
        return update(request.version(), request.values());
    }

    public GlobalSettingsResponse update(Long version, GlobalSettingsValues values) {
        GlobalSettings settings = load();
        if (version != null && version != settings.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(GlobalSettings.class, GlobalSettings.ID);
        }
        ValidationProblems problems = new ValidationProblems();
        values.validate(problems);
        problems.throwIfAny();

        settings.apply(values);
        GlobalSettings saved = repository.saveAndFlush(settings);
        events.publishEvent(new GlobalSettingsChanged());
        return GlobalSettingsResponse.from(saved);
    }

    private GlobalSettings load() {
        return repository.findById(GlobalSettings.ID)
                .orElseThrow(() -> new IllegalStateException("The global settings are missing; the portal creates them at start-up"));
    }
}
