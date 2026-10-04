package com.bbh.itss.dso.portal.settings;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes sure the global settings exist before anything else runs at start-up, the demo data included.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalSettingsInitializer implements ApplicationRunner {

    private final GlobalSettingsService settings;

    public GlobalSettingsInitializer(GlobalSettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        settings.ensureExists();
    }
}
