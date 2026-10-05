package com.bbh.itss.dso.portal.application.dsoconfig.port.in;

import java.util.Map;

public interface ReadPipelineConfigUseCase {

    Map<String, Object> readByKey(String key);
}
