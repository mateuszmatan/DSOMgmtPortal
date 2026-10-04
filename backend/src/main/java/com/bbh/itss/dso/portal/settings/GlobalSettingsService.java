package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.common.ValidationProblems;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and changes the global settings. A change is announced with {@link GlobalSettingsChanged} in the same
 * transaction, since every pipeline's configuration is rendered from these settings.
 */
@Service
@Transactional
public class GlobalSettingsService {

    private final GlobalSettingsRepository repository;
    private final ApplicationEventPublisher events;

    public GlobalSettingsService(GlobalSettingsRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** Creates the settings with the DSOEnhanced defaults when the database has none yet. */
    public GlobalSettings ensureExists() {
        return repository.findById(GlobalSettings.ID)
                .orElseGet(() -> repository.saveAndFlush(new GlobalSettings(GlobalSettingsValues.bbhDefaults())));
    }

    @Transactional(readOnly = true)
    public GlobalSettings current() {
        return repository.findById(GlobalSettings.ID)
                .orElseThrow(() -> new IllegalStateException("The global settings are missing; the portal creates them at start-up"));
    }

    /** The Jenkins the pipelines run on, to link a pipeline's job path; null while it is not set. */
    @Transactional(readOnly = true)
    public String jenkinsUrl() {
        return current().platform().jenkinsUrl();
    }

    @Transactional(readOnly = true)
    public GlobalSettingsResponse get() {
        return GlobalSettingsResponse.from(current());
    }

    public GlobalSettingsResponse update(GlobalSettingsRequest request) {
        return update(request.version(), request.values());
    }

    /** @param version the version the values were edited at; null skips the concurrent change check */
    public GlobalSettingsResponse update(Long version, GlobalSettingsValues values) {
        GlobalSettings settings = current();
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
}
