package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalSettingsInitializer implements ApplicationRunner {

    private final ManageGlobalSettingsUseCase settings;

    public GlobalSettingsInitializer(ManageGlobalSettingsUseCase settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        settings.ensureExists();
    }
}
