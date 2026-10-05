package com.bbh.itss.dso.portal.adapter.out.persistence

import spock.lang.Specification

import java.time.Instant

class PublishedPipelineConfigEntitySpec extends Specification {

    static final Instant FIRST = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant LATER = Instant.parse('2026-10-04T12:00:00Z')

    def config = new PublishedPipelineConfigEntity(100L)

    def "a new row is keyed by its pipeline and must be inserted"() {
        expect:
        config.id == 100L
        config.isNew()
        config.configJson == null
        config.renderedAt == null
    }

    def "publishing takes the configuration with the time it was rendered"() {
        when:
        config.publish('{"pipeline":{"type":"full"}}', FIRST)
        config.publish('{"pipeline":{"type":"sast"}}', LATER)

        then:
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
        def loaded = PublishedPipelineConfigEntity.getDeclaredConstructor().with { accessible = true; newInstance() }

        then:
        loaded.id == null
        loaded.isNew()

        when:
        loaded.markStored()

        then:
        !loaded.isNew()
    }
}
