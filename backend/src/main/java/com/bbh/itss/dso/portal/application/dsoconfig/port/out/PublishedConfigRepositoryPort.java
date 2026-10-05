package com.bbh.itss.dso.portal.application.dsoconfig.port.out;

import com.bbh.itss.dso.portal.domain.dsoconfig.PublishedConfig;

import java.util.Optional;

public interface PublishedConfigRepositoryPort {

    Optional<PublishedConfig> load(long pipelineId);

    void save(PublishedConfig config);
}
