package com.bbh.itss.dso.portal.application.pipeline.port.in;

import java.util.List;

public interface QueryPipelinesUseCase {

    List<ServicePipelinesView> listForProduct(long productId);

    PipelineView get(long id);
}
