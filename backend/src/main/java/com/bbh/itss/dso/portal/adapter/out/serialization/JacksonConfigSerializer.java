package com.bbh.itss.dso.portal.adapter.out.serialization;

import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Component
class JacksonConfigSerializer implements ConfigSerializerPort {

    private final JsonMapper json;

    JacksonConfigSerializer(JsonMapper json) {
        this.json = json;
    }

    @Override
    public String toJson(Map<String, Object> config) {
        return json.writeValueAsString(config);
    }
}
