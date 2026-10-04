package com.bbh.itss.dso.portal.catalog;

import java.util.Map;

/**
 * Pipeline counts per product, provided by the pipeline module so the catalog does not depend on it.
 */
public interface PipelineStatistics {

    /** Product id to the number of its pipelines. */
    Map<Long, Long> pipelinesPerProduct();

    /** Product id to the number of its pipelines with an active key. */
    Map<Long, Long> activePipelinesPerProduct();
}
