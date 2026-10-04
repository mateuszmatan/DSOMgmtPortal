package com.bbh.dso.portal.pipeline;

import com.bbh.dso.portal.catalog.ServiceDefinition;

import java.time.Instant;
import java.util.List;

/**
 * A pipeline with its active key. {@code keys} holds the full key history and is filled only when a single
 * pipeline is requested.
 */
public record PipelineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        Long serviceId,
        String serviceName,
        PipelineType type,
        String entryPoint,
        List<String> agentLabels,
        String extendedPipelineJob,
        String description,
        boolean enabled,
        KeyResponse activeKey,
        String influxProjectTag,
        String influxEnv,
        Instant createdAt,
        Instant updatedAt,
        List<KeyResponse> keys) {

    public static PipelineResponse summary(Pipeline pipeline) {
        return of(pipeline, null);
    }

    public static PipelineResponse withKeys(Pipeline pipeline) {
        return of(pipeline, pipeline.getKeys().stream().map(KeyResponse::from).toList());
    }

    private static PipelineResponse of(Pipeline pipeline, List<KeyResponse> keys) {
        ServiceDefinition service = pipeline.getService();
        KeyResponse activeKey = pipeline.activeKey().map(KeyResponse::from).orElse(null);
        return new PipelineResponse(pipeline.getId(), service.getProduct().getId(), service.getProduct().getCode(),
                service.getProduct().getName(), service.getId(), service.getName(), pipeline.getType(),
                pipeline.getType().entryPoint(), pipeline.agentLabelList(), pipeline.getExtendedPipelineJob(),
                pipeline.getDescription(), activeKey != null, activeKey,
                pipeline.getType().influxProjectTag(service.getInfluxProject()), service.getInfluxEnv(),
                pipeline.getCreatedAt(), pipeline.getUpdatedAt(), keys);
    }
}
