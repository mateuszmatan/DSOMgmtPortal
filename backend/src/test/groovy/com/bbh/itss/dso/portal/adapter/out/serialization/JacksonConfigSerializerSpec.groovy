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
}
