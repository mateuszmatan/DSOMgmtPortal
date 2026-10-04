package com.bbh.itss.dso.portal.pipeline;

/**
 * Published inside the transaction that added a pipeline or changed its settings.
 */
public record PipelineChanged(Long pipelineId) {
}
