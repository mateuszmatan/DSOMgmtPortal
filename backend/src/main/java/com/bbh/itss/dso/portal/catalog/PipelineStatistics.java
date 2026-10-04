package com.bbh.itss.dso.portal.catalog;

import java.util.Map;

public interface PipelineStatistics {

    Map<Long, Long> pipelinesPerProduct();

    Map<Long, Long> activePipelinesPerProduct();
}
