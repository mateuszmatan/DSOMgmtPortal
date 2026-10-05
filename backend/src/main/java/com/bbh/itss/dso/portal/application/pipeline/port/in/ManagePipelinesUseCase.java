package com.bbh.itss.dso.portal.application.pipeline.port.in;

public interface ManagePipelinesUseCase {

    PipelineView create(long serviceId, PipelineCommand command);

    PipelineView update(long id, PipelineCommand command);

    void delete(long id);
}
