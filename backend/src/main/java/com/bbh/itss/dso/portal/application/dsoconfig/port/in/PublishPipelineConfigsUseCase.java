package com.bbh.itss.dso.portal.application.dsoconfig.port.in;

public interface PublishPipelineConfigsUseCase {

    void productChanged(long productId);

    void pipelineChanged(long pipelineId);

    void settingsChanged();

    int publishAll();
}
