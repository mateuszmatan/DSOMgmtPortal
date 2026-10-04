package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.catalog.ServiceDefinition;

import java.time.Instant;
import java.util.List;

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
        String securityPipelineJob,
        String jenkinsJob,
        String jenkinsJobUrl,
        String description,
        boolean enabled,
        KeyResponse activeKey,
        String influxProjectTag,
        String influxEnv,
        Instant createdAt,
        Instant updatedAt,
        List<KeyResponse> keys) {

    public static PipelineResponse summary(Pipeline pipeline, String jenkinsUrl) {
        return of(pipeline, jenkinsUrl, null);
    }

    public static PipelineResponse withKeys(Pipeline pipeline, String jenkinsUrl) {
        return of(pipeline, jenkinsUrl, pipeline.getKeys().stream().map(KeyResponse::from).toList());
    }

    private static PipelineResponse of(Pipeline pipeline, String jenkinsUrl, List<KeyResponse> keys) {
        ServiceDefinition service = pipeline.getService();
        PipelineSettings settings = pipeline.getSettings();
        return new PipelineResponse(pipeline.getId(), service.getProduct().getId(), service.getProduct().getCode(),
                service.getProduct().getName(), service.getId(), service.getName(), pipeline.getType(),
                pipeline.getType().entryPoint(), settings.agentLabels(), settings.extendedPipelineJob(),
                settings.securityPipelineJob(), settings.jenkinsJob(), settings.jenkinsJobUrl(jenkinsUrl),
                settings.description(), pipeline.isEnabled(), pipeline.activeKey().map(KeyResponse::from).orElse(null),
                pipeline.influxProjectTag(), pipeline.influxEnv(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(),
                keys);
    }
}
