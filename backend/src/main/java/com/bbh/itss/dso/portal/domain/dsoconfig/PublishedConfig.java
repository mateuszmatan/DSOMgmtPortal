package com.bbh.itss.dso.portal.domain.dsoconfig;

import java.time.Instant;
import java.util.Objects;

public record PublishedConfig(long pipelineId, String configJson, Instant renderedAt) {

    public PublishedConfig {
        Objects.requireNonNull(configJson, "a published configuration has its JSON");
        Objects.requireNonNull(renderedAt, "a published configuration has the time it was rendered");
    }

    public boolean holds(String renderedJson) {
        return configJson.equals(renderedJson);
    }

    public boolean renderedSince(Instant moment) {
        return !renderedAt.isBefore(moment);
    }
}
