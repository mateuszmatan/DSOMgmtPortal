package com.bbh.itss.dso.portal.domain.dsoconfig

import spock.lang.Specification

import java.time.Instant

class PublishedConfigSpec extends Specification {

    static final Instant RENDERED = Instant.parse('2026-10-01T08:00:00Z')

    def "a published configuration holds exactly the JSON it was rendered with, since every moment up to its time"() {
        given:
        def config = new PublishedConfig(100L, '{"pipeline":{"type":"full"}}', RENDERED)

        expect:
        config.holds('{"pipeline":{"type":"full"}}')
        !config.holds('{"pipeline":{"type":"sast"}}')
        !config.holds(null)
        config.renderedSince(RENDERED.minusNanos(1000))
        config.renderedSince(RENDERED)
        !config.renderedSince(RENDERED.plusNanos(1000))
    }

    def "a published configuration needs its JSON and its time"() {
        when:
        new PublishedConfig(100L, json, renderedAt)

        then:
        thrown(NullPointerException)

        where:
        json | renderedAt
        null | RENDERED
        '{}' | null
    }
}
