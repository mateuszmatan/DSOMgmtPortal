package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;

import java.time.Instant;

public record KeyResponse(
        Long id,
        String value,
        String hint,
        KeyStatus status,
        Instant issuedAt,
        Instant revokedAt,
        String revokeReason,
        Instant lastUsedAt) {

    static KeyResponse from(PipelineKey key) {
        return of(key, key.isActive() ? key.value() : null);
    }

    static KeyResponse masked(PipelineKey key) {
        return of(key, null);
    }

    private static KeyResponse of(PipelineKey key, String value) {
        return new KeyResponse(key.id(), value, key.hint(), key.status(), key.issuedAt(), key.revokedAt(),
                key.revokeReason(), key.lastUsedAt());
    }
}
