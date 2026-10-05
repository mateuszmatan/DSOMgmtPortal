package com.bbh.itss.dso.portal.application.settings

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import spock.lang.Specification

import java.time.Instant

class GlobalSettingsServiceSpec extends Specification {

    GlobalSettingsRepositoryPort repository = Mock()
    PublishPipelineConfigsUseCase publisher = Mock()
    def service = new GlobalSettingsService(repository, publisher)
    def bbh = GlobalSettingsValues.bbhDefaults()
    def stored = new GlobalSettings(bbh, 3, Instant.parse('2026-10-04T12:00:00Z'))

    def "the BBH defaults are created only when the database has no settings"() {
        when:
        def result = service.ensureExists()

        then:
        1 * repository.load() >> Optional.ofNullable(existing)
        (existing ? 0 : 1) * repository.save(GlobalSettings.bbhDefaults()) >> stored
        0 * publisher._
        result.is(existing ?: stored)

        where:
        existing << [null, new GlobalSettings(GlobalSettingsValues.bbhDefaults(), 3, Instant.EPOCH)]
    }

    def "the current settings are read from the store, and missing ones are an error of the start-up"() {
        given:
        repository.load() >>> [Optional.of(stored), Optional.empty()]

        expect:
        service.current().is(stored)

        when:
        service.current()

        then:
        thrown(MissingGlobalSettingsException)
    }

    def "a change reads the stored settings once every configuration is locked, then publishes every pipeline"() {
        given:
        def changed = bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))
        def saved = new GlobalSettings(changed, 4, Instant.EPOCH)

        when:
        def result = service.update(version, changed)

        then:
        1 * publisher.lockConfigurations()

        then:
        1 * repository.load() >> Optional.of(stored)

        then:
        1 * repository.save(new GlobalSettings(changed, 3, stored.updatedAt())) >> saved

        then:
        1 * publisher.settingsChanged()
        result.is(saved)

        where:
        version << [3L, null]
    }

    def "a refused change is neither saved nor published"() {
        given:
        repository.load() >> Optional.of(stored)

        when:
        service.update(2L, bbh)

        then:
        thrown(ConflictException)
        0 * repository.save(_)
        0 * publisher.settingsChanged()
    }
}
