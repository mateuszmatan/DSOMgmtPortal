package com.bbh.itss.dso.portal.application.dsoconfig.port.out;

import java.util.Map;

public interface ConfigSerializerPort {

    String toJson(Map<String, Object> config);
}
