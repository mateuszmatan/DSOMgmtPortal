package com.bbh.itss.dso.portal.settings

import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.settings.Scanner.SCA

class GlobalSettingsSpec extends Specification {

    def bbh = GlobalSettingsValues.bbhDefaults()

    def "new settings take every value under the fixed key"() {
        when:
        def settings = new GlobalSettings(bbh)

        then:
        settings.id == GlobalSettings.ID
        settings.id == 1L
        settings.values() == bbh
        settings.platform() == bbh.platform()
        settings.deployment() == bbh.deployment()
    }

    def "settings are new until they were first stored"() {
        given:
        def settings = new GlobalSettings(bbh)

        expect:
        settings.isNew()

        when:
        ReflectionTestUtils.setField(settings, 'createdAt', Instant.parse('2026-10-04T12:00:00Z'))

        then:
        !settings.isNew()
    }

    def "the values come with their limits in scanner order"() {
        given:
        def settings = new GlobalSettings(bbh)

        expect:
        settings.values().limits().keySet() as List == [SAST, SCA, NEXUS_IQ, DAST]
    }

    def "applying values replaces every section"() {
        given:
        def settings = new GlobalSettings(bbh)
        def changed = new GlobalSettingsValues(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'),
                new DeploymentDefaults('ucd.bbh.com', 'deploy', 'rd.bbh.com', 'qc.bbh.com', 'svc', 'deploy.sh', 'v.txt'),
                [(SAST): new SeverityLimits(1, 1, 1), (SCA): SeverityLimits.ZERO, (NEXUS_IQ): SeverityLimits.ZERO,
                 (DAST): SeverityLimits.ZERO],
                new ScanSettings(70, 10, 10, 10, false, 10, 10, 10, 10, 10, 10, false, 10),
                new ReleaseGateSettings([SAST], false, 'gate.json'),
                new ServiceDefaults(bbh.serviceDefaults().buildTool(), bbh.serviceDefaults().deployTarget(), 'src', 5),
                bbh.goldenFix())

        when:
        settings.apply(changed)

        then:
        settings.values() == changed
        settings.platform().jenkinsUrl() == 'https://jenkins.bbh.com'
        settings.deployment().urbanCodeSiteName() == 'ucd.bbh.com'
    }

    def "unchanged limits are left alone so saving writes no limit rows"() {
        given:
        def settings = new GlobalSettings(bbh)
        Map<Scanner, SeverityLimits> stored = Spy(HashMap, constructorArgs: [bbh.limits()])
        ReflectionTestUtils.setField(settings, 'limits', stored)

        when:
        settings.apply(bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com')))

        then:
        0 * stored.clear()
        0 * stored.putAll(_)
        settings.platform().jenkinsUrl() == 'https://jenkins.bbh.com'

        when:
        settings.apply(new GlobalSettingsValues(bbh.platform(), bbh.deployment(), [(SAST): new SeverityLimits(3, 2, 1)],
                bbh.scans(), bbh.releaseGate(), bbh.serviceDefaults(), bbh.goldenFix()))

        then:
        1 * stored.clear()
        1 * stored.putAll({ it == [(SAST): new SeverityLimits(3, 2, 1)] })
        settings.values().limits() == [(SAST): new SeverityLimits(3, 2, 1)]
    }

    def "the settings JPA loads start empty"() {
        when:
        def loaded = GlobalSettings.getDeclaredConstructor().with { accessible = true; newInstance() }

        then:
        loaded.id == 1L
        loaded.isNew()
        loaded.platform() == null
        loaded.values().limits() == [:]
    }
}
