package com.bbh.itss.dso.portal.pipeline;

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
        return new KeyResponse(key.getId(), key.getValue(), key.getStatus(), key.getIssuedAt(), key.getRevokedAt(),
                key.getRevokeReason(), key.getLastUsedAt());
    }
}
