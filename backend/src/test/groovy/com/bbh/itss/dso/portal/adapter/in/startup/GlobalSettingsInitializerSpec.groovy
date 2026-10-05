package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import org.springframework.boot.ApplicationArguments
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import spock.lang.Specification

class GlobalSettingsInitializerSpec extends Specification {

    ManageGlobalSettingsUseCase settings = Mock()

    def "the settings exist before anything else runs at start-up"() {
        given:
        def initializer = new GlobalSettingsInitializer(settings)

        when:
        initializer.run(Stub(ApplicationArguments))

        then:
        1 * settings.ensureExists() >> GlobalSettings.bbhDefaults()
        GlobalSettingsInitializer.getAnnotation(Order).value() == Ordered.HIGHEST_PRECEDENCE
    }
}
