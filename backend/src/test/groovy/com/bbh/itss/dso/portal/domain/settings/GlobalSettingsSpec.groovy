package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.PlatformSettingsSpec.copy

class GlobalSettingsSpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')

    def bbh = GlobalSettingsValues.bbhDefaults()
    def stored = new GlobalSettings(bbh, 3, UPDATED)

    def "new settings start as the BBH defaults before they were ever stored"() {
        when:
        def defaults = GlobalSettings.bbhDefaults()

        then:
        defaults.values() == bbh
        defaults.version() == 0
        defaults.updatedAt() == null
    }

    def "the settings cannot exist without values"() {
        when:
        new GlobalSettings(null, 0, null)

        then:
        def e = thrown(NullPointerException)
        e.message == 'values'
    }

    def "the platform, the deployment defaults and the Jenkins URL are read from the values"() {
        given:
        def withJenkins = new GlobalSettings(bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com')), 1,
                UPDATED)

        expect:
        stored.platform() == bbh.platform()
        stored.deployment() == bbh.deployment()
        stored.jenkinsUrl() == null
        withJenkins.jenkinsUrl() == 'https://jenkins.bbh.com'
    }

    def "a change read at the current version takes the new values and keeps the version for the store to raise"() {
        given:
        def changed = bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))

        when:
        def result = stored.change(3L, changed)

        then:
        result == new GlobalSettings(changed, 3, UPDATED)
        stored.values() == bbh
    }

    def "a change without a version skips the concurrent change check"() {
        expect:
        stored.change(null, bbh) == stored
    }

    def "a change read at an older version is refused"() {
        when:
        stored.change(2L, bbh)

        then:
        def e = thrown(ConflictException)
        e.message == 'The record was changed by someone else in the meantime. Reload it and apply your change again.'
    }

    def "a change breaking the business rules is refused with every problem"() {
        given:
        def invalid = new GlobalSettingsValues(copy(bbh.platform(), proxyHost: null), bbh.deployment(),
                bbh.limits().findAll { it.key != Scanner.DAST }, bbh.scans(), bbh.releaseGate(), bbh.serviceDefaults(),
                bbh.goldenFix())

        when:
        stored.change(3L, invalid)

        then:
        def e = thrown(InvalidRequestException)
        e.message == '2 fields are invalid'
        e.problems == [new FieldProblem('platform.proxyHost', 'is required with a proxy port'),
                       new FieldProblem('limits.DAST', 'set the limits of every scanner')]
    }

    def "an incomplete GoldenFix policy names the missing value"() {
        given:
        def incomplete = new GlobalSettingsValues(bbh.platform(), bbh.deployment(), bbh.limits(), bbh.scans(),
                bbh.releaseGate(), bbh.serviceDefaults(), copy(bbh.goldenFix(), commitAuthorEmail: null) as GoldenFixPolicy)

        when:
        stored.change(3L, incomplete)

        then:
        def e = thrown(InvalidRequestException)
        e.message == 'is required in the global settings'
        e.problems == [new FieldProblem('goldenFix.commitAuthorEmail', 'is required in the global settings')]
    }

    def "missing settings explain that the portal creates them at start-up"() {
        expect:
        new MissingGlobalSettingsException().message ==
                'The global settings are missing; the portal creates them at start-up'
        new MissingGlobalSettingsException() instanceof IllegalStateException
    }
}
