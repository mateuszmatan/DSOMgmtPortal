package com.bbh.itss.dso.portal.domain.pipeline;

import java.util.Objects;

public record IssuedKey(long pipelineId, PipelineKey key) {

    public IssuedKey {
        Objects.requireNonNull(key, "an issued key names the key");
    }

    public long authorize() {
        key.requireActive();
        return pipelineId;
    }
}
