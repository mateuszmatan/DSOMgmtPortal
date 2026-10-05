package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "DSO_PIPELINE_KEY")
public class PipelineKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PIPELINE_ID", nullable = false)
    private PipelineEntity pipeline;

    @Column(name = "KEY_VALUE", nullable = false, length = 36, updatable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private KeyStatus status;

    @Column(name = "ISSUED_AT", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "REVOKED_AT")
    private Instant revokedAt;

    @Column(name = "REVOKE_REASON", length = 500)
    private String revokeReason;

    @Column(name = "LAST_USED_AT")
    private Instant lastUsedAt;

    protected PipelineKeyEntity() {
    }

    PipelineKeyEntity(PipelineEntity pipeline, String value, Instant issuedAt) {
        this.pipeline = pipeline;
        this.value = value;
        this.issuedAt = issuedAt;
    }

    void state(KeyStatus status, Instant revokedAt, String revokeReason) {
        this.status = status;
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
    }

    Long getId() {
        return id;
    }

    PipelineEntity pipeline() {
        return pipeline;
    }

    String value() {
        return value;
    }

    KeyStatus status() {
        return status;
    }

    Instant issuedAt() {
        return issuedAt;
    }

    Instant revokedAt() {
        return revokedAt;
    }

    String revokeReason() {
        return revokeReason;
    }

    Instant lastUsedAt() {
        return lastUsedAt;
    }
}
