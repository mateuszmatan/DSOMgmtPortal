package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.adapter.out.persistence.GlobalSettingsEntity.SeverityLimitsEmbeddable
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.GoldenFixPolicyEmbeddable
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.settings.SeverityLimits
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA

class GlobalSettingsEntitySpec extends Specification {

    static final Instant UPDATED = Instant.parse('2026-10-04T12:00:00Z')

    def entity = new GlobalSettingsEntity()
    def bbh = GlobalSettingsValues.bbhDefaults()

    def "the settings live under one fixed key and are new until they were first stored"() {
        expect:
        entity.id == GlobalSettingsEntity.ID
        entity.isNew()

        when:
        ReflectionTestUtils.setField(entity, 'createdAt', UPDATED)

        then:
        !entity.isNew()
    }

    def "every section is copied into the columns and read back unchanged with the stored version"() {
        given:
        ReflectionTestUtils.setField(entity, 'version', 7L)
        ReflectionTestUtils.setField(entity, 'updatedAt', UPDATED)

        when:
        entity.apply(bbh)
        def read = entity.toDomain()

        then:
        read.values() == bbh
        read.version() == 7
        read.updatedAt() == UPDATED
        read.values().limits().keySet() as List == [SAST, SCA, NEXUS_IQ, DAST]
        ReflectionTestUtils.getField(entity, 'values').releaseGate().scanners() == [SAST, SCA, NEXUS_IQ, DAST]
    }

    def "unchanged limits are left alone so saving writes no limit rows"() {
        given:
        entity.apply(bbh)
        Map stored = Spy(HashMap, constructorArgs: [ReflectionTestUtils.getField(entity, 'limits')])
        ReflectionTestUtils.setField(entity, 'limits', stored)

        when:
        entity.apply(bbh)

        then:
        0 * stored.clear()
        0 * stored.putAll(_)

        when:
        entity.apply(new GlobalSettingsValues(bbh.platform(), bbh.deployment(), [(SAST): new SeverityLimits(3, 2, 1)],
                bbh.scans(), bbh.releaseGate(), bbh.serviceDefaults(), bbh.goldenFix()))

        then:
        1 * stored.clear()
        1 * stored.putAll({ it == [(SAST): new SeverityLimitsEmbeddable(3, 2, 1)] })
    }

    def "a GoldenFix policy keeps every value through its columns"() {
        expect:
        RecordMapper.map(RecordMapper.map(policy, GoldenFixPolicyEmbeddable), GoldenFixPolicy) == policy

        where:
        policy << [GoldenFixPolicy.INHERITED,
                   new GoldenFixPolicy(false, true, 8, ['maven'], ['recommended-non-breaking'], ['legacy'], true, 2, 30,
                           'mvn verify', 'gradle check', 'npm test', 'pytest', 'flutter test', 'Bot', 'bot@bbh.com', 'UTC')]
    }
}
