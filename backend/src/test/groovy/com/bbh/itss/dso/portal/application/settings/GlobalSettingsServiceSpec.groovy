package com.bbh.itss.dso.portal.application.settings

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.UpdateGlobalSettingsCommand
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException
import com.bbh.itss.dso.portal.domain.settings.Scanner
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class GlobalSettingsServiceSpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')

    GlobalSettingsRepositoryPort repository = Mock()
    PublishPipelineConfigsUseCase publisher = Mock()

    @Subject
    def service = new GlobalSettingsService(repository, publisher)

    def bbh = GlobalSettingsValues.bbhDefaults()
    def stored = new GlobalSettings(bbh, 3, UPDATED)

    def "the BBH defaults are created when the database has no settings"() {
        given:
        def created = new GlobalSettings(bbh, 0, UPDATED)

        when:
        def result = service.ensureExists()

        then:
        1 * repository.load() >> Optional.empty()
        1 * repository.save(GlobalSettings.bbhDefaults()) >> created
        0 * publisher._
        result.is(created)
    }

    def "existing settings are kept"() {
        when:
        def result = service.ensureExists()

        then:
        1 * repository.load() >> Optional.of(stored)
        0 * repository.save(_)
        result.is(stored)
    }

    def "the current settings are read from the store"() {
        given:
        repository.load() >> Optional.of(stored)

        expect:
        service.current().is(stored)
    }

    def "missing settings are an error of the start-up"() {
        given:
        repository.load() >> Optional.empty()

        when:
        service.current()

        then:
        def e = thrown(MissingGlobalSettingsException)
        e.message == 'The global settings are missing; the portal creates them at start-up'
    }

    def "a change read at the current version is saved, then every pipeline is published again"() {
        given:
        def changed = bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))
        def saved = new GlobalSettings(changed, 4, UPDATED.plusSeconds(60))
        repository.load() >> Optional.of(stored)

        when:
        def result = service.update(new UpdateGlobalSettingsCommand(3L, changed))

        then:
        1 * repository.save(new GlobalSettings(changed, 3, UPDATED)) >> saved

        then:
        1 * publisher.settingsChanged()
        result.is(saved)
    }

    def "a change reads the stored settings only once every pipeline configuration is locked"() {
        when:
        service.update(new UpdateGlobalSettingsCommand(3L, bbh))

        then:
        1 * publisher.lockConfigurations()

        then:
        1 * repository.load() >> Optional.of(stored)

        then:
        1 * repository.save(stored) >> stored

        then:
        1 * publisher.settingsChanged()
    }

    def "a change without a version skips the concurrent change check"() {
        given:
        repository.load() >> Optional.of(stored)

        when:
        service.update(UpdateGlobalSettingsCommand.unversioned(bbh))

        then:
        1 * repository.save(stored) >> stored
        1 * publisher.settingsChanged()
    }

    def "a change read at an older version is refused before anything is saved or published"() {
        given:
        repository.load() >> Optional.of(stored)

        when:
        service.update(new UpdateGlobalSettingsCommand(2L, bbh))

        then:
        thrown(ConflictException)
        0 * repository.save(_)
        0 * publisher.settingsChanged()
    }

    def "a change breaking the business rules is refused before anything is saved or published"() {
        given:
        repository.load() >> Optional.of(stored)
        def invalid = new GlobalSettingsValues(bbh.platform(), bbh.deployment(),
                bbh.limits().findAll { it.key != Scanner.DAST }, bbh.scans(), bbh.releaseGate(), bbh.serviceDefaults(),
                bbh.goldenFix())

        when:
        service.update(new UpdateGlobalSettingsCommand(3L, invalid))

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['limits.DAST']
        0 * repository.save(_)
        0 * publisher.settingsChanged()
    }

    def "a command needs the values"() {
        when:
        new UpdateGlobalSettingsCommand(1L, null)

        then:
        def e = thrown(NullPointerException)
        e.message == 'values'
    }
}
