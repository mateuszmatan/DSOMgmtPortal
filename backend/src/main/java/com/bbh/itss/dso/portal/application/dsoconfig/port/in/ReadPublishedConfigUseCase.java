package com.bbh.itss.dso.portal.application.dsoconfig.port.in;

import java.util.Map;
import java.util.Optional;

public interface ReadPublishedConfigUseCase {

    Optional<Map<String, Object>> currentConfig(long pipelineId);
}
