package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;

import java.time.Instant;

public record KeyResponse(
        Long id,
        String value,
        KeyStatus status,
        Instant issuedAt,
        Instant revokedAt,
        String revokeReason,
        Instant lastUsedAt) {

    static KeyResponse from(PipelineKey key) {
        return new KeyResponse(key.id(), key.value(), key.status(), key.issuedAt(), key.revokedAt(), key.revokeReason(),
                key.lastUsedAt());
    }
}
