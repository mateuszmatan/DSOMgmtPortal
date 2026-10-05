package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import spock.lang.Specification

class StartupConfigPublisherSpec extends Specification {

    PublishPipelineConfigsUseCase publisher = Mock()

    def "every pipeline is published again once the portal is ready"() {
        when:
        new StartupConfigPublisher(publisher).publishAll()

        then:
        1 * publisher.publishAll() >> 3
        StartupConfigPublisher.getMethod('publishAll').getAnnotation(EventListener).value() as List == [ApplicationReadyEvent]
    }
}
