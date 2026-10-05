package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

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

    public static PipelineResponse summary(PipelineView view) {
        return of(view, null);
    }

    public static PipelineResponse withKeys(PipelineView view) {
        return of(view, view.pipeline().keys().stream().map(KeyResponse::from).toList());
    }

    private static PipelineResponse of(PipelineView view, List<KeyResponse> keys) {
        Pipeline pipeline = view.pipeline();
        PipelineSettings settings = pipeline.settings();
        return new PipelineResponse(pipeline.id(), view.product().id(), view.product().code(), view.product().name(),
                view.service().id(), view.service().name(), pipeline.type(), pipeline.type().entryPoint(),
                settings.agentLabels(), settings.extendedPipelineJob(), settings.securityPipelineJob(),
                settings.jenkinsJob(), view.jenkinsJobUrl(), settings.description(), pipeline.isEnabled(),
                pipeline.activeKey().map(KeyResponse::from).orElse(null), view.influxProjectTag(), view.influxEnv(),
                pipeline.createdAt(), pipeline.updatedAt(), keys);
    }
}
