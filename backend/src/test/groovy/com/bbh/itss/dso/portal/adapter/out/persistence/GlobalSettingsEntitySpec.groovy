package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.settings.Scanner
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST

class GlobalSettingsEntitySpec extends Specification {

    def entity = new GlobalSettingsEntity()

    def "the settings live under one fixed key and start empty"() {
        expect:
        entity.id == 1L
        GlobalSettingsEntity.ID == 1L
        entity.platform() == null
        entity.goldenFix() == null
        entity.limits() == [:]
    }

    def "the settings are new until they were first stored"() {
        expect:
        entity.isNew()

        when:
        ReflectionTestUtils.setField(entity, 'createdAt', Instant.parse('2026-10-04T12:00:00Z'))

        then:
        !entity.isNew()
    }

    def "unchanged limits are left alone so saving writes no limit rows"() {
        given:
        def zero = new SeverityLimitsEmbeddable(0, 0, 0)
        Map<Scanner, SeverityLimitsEmbeddable> stored = Spy(HashMap, constructorArgs: [[(SAST): zero, (DAST): zero]])
        ReflectionTestUtils.setField(entity, 'limits', stored)

        when:
        entity.limits(new EnumMap<>([(SAST): zero, (DAST): zero]))

        then:
        0 * stored.clear()
        0 * stored.putAll(_)

        when:
        entity.limits([(SAST): new SeverityLimitsEmbeddable(3, 2, 1)])

        then:
        1 * stored.clear()
        1 * stored.putAll({ it == [(SAST): new SeverityLimitsEmbeddable(3, 2, 1)] })
        entity.limits() == [(SAST): new SeverityLimitsEmbeddable(3, 2, 1)]
    }
}
