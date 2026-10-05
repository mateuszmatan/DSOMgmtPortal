package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

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
        return of(view, KeyResponse::from, null);
    }

    public static PipelineResponse withKeys(PipelineView view) {
        return of(view, KeyResponse::from, view.pipeline().keys().stream().map(KeyResponse::from).toList());
    }

    public static PipelineResponse monitored(PipelineView view) {
        return of(view, KeyResponse::masked, List.of());
    }

    private static PipelineResponse of(PipelineView view, Function<PipelineKey, KeyResponse> activeKey,
                                       List<KeyResponse> keys) {
        Pipeline pipeline = view.pipeline();
        PipelineSettings settings = pipeline.settings();
        return new PipelineResponse(pipeline.id(), view.product().id(), view.product().code(), view.product().name(),
                view.service().id(), view.service().name(), pipeline.type(), pipeline.type().entryPoint(),
                settings.agentLabels(), settings.extendedPipelineJob(), settings.securityPipelineJob(),
                settings.jenkinsJob(), view.jenkinsJobUrl(), settings.description(), pipeline.isEnabled(),
                pipeline.activeKey().map(activeKey).orElse(null), view.influxProjectTag(), view.influxEnv(),
                pipeline.createdAt(), pipeline.updatedAt(), keys);
    }
}
