package com.bbh.itss.dso.portal.adapter.out.serialization

import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class JacksonConfigSerializerSpec extends Specification {

    def serializer = new JacksonConfigSerializer(JsonMapper.builder().build())

    def "a configuration becomes compact JSON in the order of its keys"() {
        given:
        def config = new LinkedHashMap<String, Object>()
        config.pipeline = [type: 'full', agentNames: ['linux-agent']]
        config.projects = [gui: [appId: 'a-1', coverage: [minLine: 60]]]

        expect:
        serializer.toJson(config) ==
                '{"pipeline":{"type":"full","agentNames":["linux-agent"]},"projects":{"gui":{"appId":"a-1","coverage":{"minLine":60}}}}'
    }

    def "published JSON reads back as the configuration in the order of its keys"() {
        given:
        def text = '{"pipeline":{"type":"full","agentNames":["linux-agent"]},"projects":{"gui":{"enabled":true,"coverage":{"minLine":60}},"api":{}}}'

        when:
        def config = serializer.fromJson(text)

        then:
        config.keySet() as List == ['pipeline', 'projects']
        config.pipeline.keySet() as List == ['type', 'agentNames']
        config.projects.keySet() as List == ['gui', 'api']
        config.projects.gui.coverage.minLine == 60
        config.projects.gui.enabled == true
        serializer.toJson(config) == text
    }
}
