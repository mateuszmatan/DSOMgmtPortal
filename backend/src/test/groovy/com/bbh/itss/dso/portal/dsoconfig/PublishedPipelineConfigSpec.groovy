package com.bbh.itss.dso.portal.dsoconfig

import spock.lang.Specification

import java.time.Instant

class PublishedPipelineConfigSpec extends Specification {

    static final Instant FIRST = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant LATER = Instant.parse('2026-10-04T12:00:00Z')

    def config = new PublishedPipelineConfig(100L)

    def "a new row is keyed by its pipeline and must be inserted"() {
        expect:
        config.id == 100L
        config.pipelineId == 100L
        config.isNew()
        config.configJson == null
        config.renderedAt == null
    }

    def "the first configuration is taken with its time"() {
        when:
        def changed = config.publish('{"pipeline":{"type":"full"}}', FIRST)

        then:
        changed
        config.configJson == '{"pipeline":{"type":"full"}}'
        config.renderedAt == FIRST
    }

    def "the same configuration again is no change and keeps the time it was rendered"() {
        given:
        config.publish('{"pipeline":{"type":"full"}}', FIRST)

        when:
        def changed = config.publish('{"pipeline":{"type":"full"}}', LATER)

        then:
        !changed
        config.renderedAt == FIRST
    }

    def "a different configuration replaces the stored one and its time"() {
        given:
        config.publish('{"pipeline":{"type":"full"}}', FIRST)

        when:
        def changed = config.publish('{"pipeline":{"type":"sast"}}', LATER)

        then:
        changed
        config.configJson == '{"pipeline":{"type":"sast"}}'
        config.renderedAt == LATER
    }

    def "a row that was loaded or stored is no longer new"() {
        when:
        config.markStored()

        then:
        !config.isNew()
        config.id == 100L
    }

    def "a row JPA creates is new until it is loaded"() {
        when:
        def loaded = PublishedPipelineConfig.getDeclaredConstructor().with { accessible = true; newInstance() }

        then:
        loaded.id == null
        loaded.isNew()

        when:
        loaded.markStored()

        then:
        !loaded.isNew()
    }
}
