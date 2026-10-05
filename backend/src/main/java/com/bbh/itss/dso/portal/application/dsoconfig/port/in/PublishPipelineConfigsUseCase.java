package com.bbh.itss.dso.portal.application.dsoconfig.port.in;

import java.util.Map;
import java.util.Optional;

public interface PublishPipelineConfigsUseCase {

    void lockConfigurations();

    void productChanged(long productId);

    void pipelineChanged(long pipelineId);

    void settingsChanged();

    int publishAll();

    Optional<Map<String, Object>> currentConfig(long pipelineId);
}
