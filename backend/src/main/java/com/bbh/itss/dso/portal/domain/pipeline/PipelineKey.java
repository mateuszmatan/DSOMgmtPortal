package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.Text;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record PipelineKey(
        Long id,
        String value,
        KeyStatus status,
        Instant issuedAt,
        Instant revokedAt,
        String revokeReason,
        Instant lastUsedAt) {

    public PipelineKey {
        Objects.requireNonNull(value, "a key needs its value");
        Objects.requireNonNull(status, "a key needs its status");
        Objects.requireNonNull(issuedAt, "a key needs the time it was issued");
        revokeReason = revokeReason == null ? null : revokeReason.trim();
        if ((status == KeyStatus.REVOKED) != (revokedAt != null)) {
            throw new IllegalArgumentException("a key carries a revocation time exactly when it is revoked");
        }
    }

    static PipelineKey issue(String value, Instant now) {
        return new PipelineKey(null, value, KeyStatus.ACTIVE, now, null, null, null);
    }

    public static String normalize(String value) {
        return Text.isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == KeyStatus.ACTIVE;
    }

    PipelineKey revoke(String reason, Instant now) {
        return new PipelineKey(id, value, KeyStatus.REVOKED, issuedAt, now, reason, lastUsedAt);
    }

    PipelineKey usedAt(Instant now) {
        return new PipelineKey(id, value, status, issuedAt, revokedAt, revokeReason, now);
    }
}
