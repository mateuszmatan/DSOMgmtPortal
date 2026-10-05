package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA

class GlobalSettingsMapperSpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')

    @Subject
    def mapper = new GlobalSettingsMapper()

    def bbh = GlobalSettingsValues.bbhDefaults()

    def "every section is copied into the columns and read back unchanged with the stored version"() {
        given:
        def entity = new GlobalSettingsEntity()
        ReflectionTestUtils.setField(entity, 'version', 7L)
        ReflectionTestUtils.setField(entity, 'updatedAt', UPDATED)

        when:
        mapper.copy(bbh, entity)
        def read = mapper.toDomain(entity)

        then:
        read.values() == bbh
        read.version() == 7
        read.updatedAt() == UPDATED
        read.values().limits().keySet() as List == [SAST, SCA, NEXUS_IQ, DAST]
        entity.platform().jenkinsLibrary() == 'DevSecOpsJenkinsLibrary'
        entity.releaseGate().scanners() == [SAST, SCA, NEXUS_IQ, DAST]
        entity.goldenFix().ecosystems() == ['maven', 'npm', 'pypi']
    }

    def "a service's GoldenFix override keeps every value through its columns"() {
        given:
        def policy = new GoldenFixPolicy(false, true, 8, ['maven'], ['recommended-non-breaking'], ['legacy'], true, 2, 30,
                'mvn verify', 'gradle check', 'npm test', 'pytest', 'flutter test', 'Bot', 'bot@bbh.com', 'UTC')

        expect:
        GoldenFixPolicyEmbeddable.of(policy).toDomain() == policy
        GoldenFixPolicyEmbeddable.of(GoldenFixPolicy.INHERITED).toDomain() == GoldenFixPolicy.INHERITED
    }
}
