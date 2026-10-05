package com.bbh.itss.dso.portal.application.evidence.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Service;

import java.util.List;
import java.util.Objects;

public record ServiceEvidence(Service service, List<PipelineEvidence> pipelines) {

    public ServiceEvidence {
        Objects.requireNonNull(service, "evidence belongs to a service");
        pipelines = List.copyOf(pipelines);
    }
}
