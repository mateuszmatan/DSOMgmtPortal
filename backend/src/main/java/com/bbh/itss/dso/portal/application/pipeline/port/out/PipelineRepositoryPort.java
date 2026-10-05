package com.bbh.itss.dso.portal.application.pipeline.port.out;

import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PipelineRepositoryPort {

    Optional<Pipeline> load(long id);

    Optional<Pipeline> loadForUpdate(long id);

    Optional<Pipeline> findByKey(String keyValue);

    List<Pipeline> findByProductId(long productId);

    List<Pipeline> findAll();

    boolean existsForService(long serviceId, PipelineType type);

    Pipeline save(Pipeline pipeline);

    void delete(long id);

    void recordKeyUse(long keyId, Instant usedAt);
}
