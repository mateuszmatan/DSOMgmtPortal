package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupConfigPublisher {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigPublisher.class);

    private final PublishPipelineConfigsUseCase publisher;

    public StartupConfigPublisher(PublishPipelineConfigsUseCase publisher) {
        this.publisher = publisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishAll() {
        log.info("Published the configuration of {} pipelines", publisher.publishAll());
    }
}
