package com.bbh.itss.dso.portal.settings

import com.bbh.itss.dso.portal.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.common.InvalidRequestException
import com.bbh.itss.dso.portal.common.InvalidRequestException.FieldProblem
import org.springframework.context.ApplicationEventPublisher
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.dso.portal.settings.PlatformSettingsSpec.copy

class GlobalSettingsServiceSpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')

    GlobalSettingsRepository repository = Mock()
    ApplicationEventPublisher events = Mock()

    @Subject
    def service = new GlobalSettingsService(repository, events)

    def bbh = GlobalSettingsValues.bbhDefaults()
    def stored = storedSettings(bbh, 3)

    def "the BBH defaults are created when the database has no settings"() {
        when:
        def created = service.ensureExists()

        then:
        1 * repository.findById(1L) >> Optional.empty()
        1 * repository.saveAndFlush({ GlobalSettings s -> s.id == 1L && s.isNew() && s.values() == bbh }) >> { GlobalSettings s -> s }
        created.values() == bbh
        0 * events._
    }

    def "existing settings are kept"() {
        when:
        def existing = service.ensureExists()

        then:
        1 * repository.findById(1L) >> Optional.of(stored)
        0 * repository.saveAndFlush(_)
        existing.is(stored)
    }

    def "the values and the Jenkins URL are read from the stored settings"() {
        given:
        repository.findById(1L) >> Optional.of(storedSettings(bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com')), 1))

        expect:
        service.values().platform().jenkinsUrl() == 'https://jenkins.bbh.com'
        service.values().limits() == bbh.limits()
        service.jenkinsUrl() == 'https://jenkins.bbh.com'
    }

    def "the Jenkins URL is null while it is not set"() {
        given:
        repository.findById(1L) >> Optional.of(stored)

        expect:
        service.jenkinsUrl() == null
    }

    def "the settings are returned with their version and modification time"() {
        given:
        repository.findById(1L) >> Optional.of(stored)

        when:
        def response = service.get()

        then:
        response == new GlobalSettingsResponse(3, UPDATED, bbh.platform(), bbh.deployment(), bbh.limits(), bbh.scans(),
                bbh.releaseGate(), bbh.serviceDefaults(), bbh.goldenFix())
    }

    def "missing settings are an error of the start-up"() {
        given:
        repository.findById(1L) >> Optional.empty()

        when:
        service.values()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The global settings are missing; the portal creates them at start-up'
    }

    def "a change read at the current version is saved and announced"() {
        given:
        def changed = bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))
        repository.findById(1L) >> Optional.of(stored)

        when:
        def response = service.update(request(3L, changed))

        then:
        1 * repository.saveAndFlush(stored) >> stored

        then:
        1 * events.publishEvent(new GlobalSettingsChanged())
        response.platform.jenkinsUrl() == 'https://jenkins.bbh.com'
        response.version == 3
        stored.values() == changed
    }

    def "a change without a version skips the concurrent change check"() {
        given:
        repository.findById(1L) >> Optional.of(stored)

        when:
        service.update(null, bbh)

        then:
        1 * repository.saveAndFlush(stored) >> stored
        1 * events.publishEvent(new GlobalSettingsChanged())
    }

    def "a change read at an older version is refused"() {
        given:
        repository.findById(1L) >> Optional.of(stored)

        when:
        service.update(request(2L, bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))))

        then:
        def e = thrown(ObjectOptimisticLockingFailureException)
        e.persistentClassName == GlobalSettings.name
        e.identifier == 1L
        0 * repository.saveAndFlush(_)
        0 * events._
        stored.platform().jenkinsUrl() == null
    }

    def "a change breaking the business rules is refused with every problem"() {
        given:
        repository.findById(1L) >> Optional.of(stored)
        def invalid = new GlobalSettingsValues(copy(bbh.platform(), proxyHost: null), bbh.deployment(),
                bbh.limits().findAll { it.key != Scanner.DAST }, bbh.scans(), bbh.releaseGate(), bbh.serviceDefaults(),
                bbh.goldenFix())

        when:
        service.update(3L, invalid)

        then:
        def e = thrown(InvalidRequestException)
        e.message == '2 fields are invalid'
        e.problems == [new FieldProblem('platform.proxyHost', 'is required with a proxy port'),
                       new FieldProblem('limits.DAST', 'set the limits of every scanner')]
        0 * repository.saveAndFlush(_)
        0 * events._
        stored.values() == bbh
    }

    def "an incomplete GoldenFix policy names the first missing value"() {
        given:
        repository.findById(1L) >> Optional.of(stored)
        def golden = bbh.goldenFix()
        def incomplete = new GlobalSettingsValues(bbh.platform(), bbh.deployment(), bbh.limits(), bbh.scans(),
                bbh.releaseGate(), bbh.serviceDefaults(), copy(golden, commitAuthorEmail: null) as GoldenFixPolicy)

        when:
        service.update(3L, incomplete)

        then:
        def e = thrown(InvalidRequestException)
        e.message == 'is required in the global settings'
        e.problems == [new FieldProblem('goldenFix.commitAuthorEmail', 'is required in the global settings')]
    }

    private static GlobalSettings storedSettings(GlobalSettingsValues values, long version) {
        def settings = new GlobalSettings(values)
        ReflectionTestUtils.setField(settings, 'createdAt', UPDATED.minusSeconds(3600))
        ReflectionTestUtils.setField(settings, 'updatedAt', UPDATED)
        ReflectionTestUtils.setField(settings, 'version', version)
        settings
    }

    private static GlobalSettingsRequest request(Long version, GlobalSettingsValues values) {
        new GlobalSettingsRequest(version, values.platform(), values.deployment(), values.limits(), values.scans(),
                values.releaseGate(), values.serviceDefaults(), values.goldenFix())
    }
}
