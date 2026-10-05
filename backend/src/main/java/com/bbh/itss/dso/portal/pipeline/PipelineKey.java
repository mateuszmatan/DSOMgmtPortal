package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.domain.shared.Timestamps;
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
import java.util.UUID;

@Entity
@Table(name = "DSO_PIPELINE_KEY")
public class PipelineKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PIPELINE_ID", nullable = false)
    private Pipeline pipeline;

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

    protected PipelineKey() {
    }

    PipelineKey(Pipeline pipeline) {
        this.pipeline = pipeline;
        this.value = UUID.randomUUID().toString();
        this.status = KeyStatus.ACTIVE;
        this.issuedAt = Timestamps.now();
    }

    void revoke(String reason) {
        status = KeyStatus.REVOKED;
        revokedAt = Timestamps.now();
        revokeReason = reason == null ? null : reason.trim();
    }

    public void markUsed() {
        lastUsedAt = Timestamps.now();
    }

    public boolean isActive() {
        return status == KeyStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public String getValue() {
        return value;
    }

    public KeyStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
