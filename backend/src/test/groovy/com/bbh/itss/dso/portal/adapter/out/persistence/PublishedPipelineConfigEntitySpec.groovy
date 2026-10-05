package com.bbh.itss.dso.portal.adapter.out.persistence

import spock.lang.Specification

import java.time.Instant

class PublishedPipelineConfigEntitySpec extends Specification {

    def "a row is new until it was loaded or stored and keeps the latest configuration with its render time"() {
        given:
        def config = new PublishedPipelineConfigEntity(100L)
        def loaded = PublishedPipelineConfigEntity.getDeclaredConstructor().with { accessible = true; newInstance() }

        when:
        config.publish('{"pipeline":{"type":"full"}}', Instant.EPOCH)
        config.publish('{"pipeline":{"type":"sast"}}', Instant.parse('2026-10-04T12:00:00Z'))

        then:
        [config.id, config.isNew(), loaded.id, loaded.isNew()] == [100L, true, null, true]
        config.configJson == '{"pipeline":{"type":"sast"}}'
        config.renderedAt == Instant.parse('2026-10-04T12:00:00Z')

        when:
        config.markStored()
        loaded.markStored()

        then:
        !config.isNew()
        !loaded.isNew()
    }
}
