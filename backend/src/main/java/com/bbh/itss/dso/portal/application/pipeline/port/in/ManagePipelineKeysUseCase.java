package com.bbh.itss.dso.portal.application.pipeline.port.in;

public interface ManagePipelineKeysUseCase {

    PipelineView issueKey(long pipelineId);

    PipelineView revokeKey(long pipelineId, String reason);

    PipelineView resolveKey(String keyValue);
}
