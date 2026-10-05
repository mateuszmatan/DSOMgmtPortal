package com.bbh.itss.dso.portal.application.pipeline.port.in;

import java.util.List;

public interface ManagePipelinesUseCase {

    PipelineView create(long serviceId, PipelineCommand command);

    List<PipelineView> createForNewServices(long productId, List<Long> serviceIds);

    PipelineView update(long id, PipelineCommand command);

    void delete(long id);
}
