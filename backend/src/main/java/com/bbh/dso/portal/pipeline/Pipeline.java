package com.bbh.dso.portal.pipeline;

import com.bbh.dso.portal.catalog.ServiceDefinition;
import com.bbh.dso.portal.common.AuditedEntity;
import com.bbh.dso.portal.common.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A DevSecOps pipeline of one service. The Jenkins job identifies itself with the pipeline's active key and
 * everything else is read from the portal. A pipeline has at most one active key; revoked keys stay as the
 * audit trail.
 */
@Entity
@Table(name = "DSO_PIPELINE")
public class Pipeline extends AuditedEntity {

    static final String REPLACED_REASON = "Replaced by a new key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SERVICE_ID", nullable = false)
    private ServiceDefinition service;

    @Enumerated(EnumType.STRING)
    @Column(name = "PIPELINE_TYPE", nullable = false, length = 20)
    private PipelineType type;

    @Embedded
    private PipelineSettings settings;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("issuedAt DESC, id DESC")
    private List<PipelineKey> keys = new ArrayList<>();

    protected Pipeline() {
    }

    /** Creates the pipeline with its first active key. */
    public Pipeline(ServiceDefinition service, PipelineType type, PipelineSettings settings) {
        this.service = service;
        this.type = type;
        configure(settings);
        issueKey();
    }

    public void configure(PipelineSettings settings) {
        this.settings = settings.forType(type);
    }

    /** Issues a new key; the active one, if any, is revoked as replaced. */
    public PipelineKey issueKey() {
        activeKey().ifPresent(active -> active.revoke(REPLACED_REASON));
        PipelineKey key = new PipelineKey(this);
        keys.addFirst(key);
        return key;
    }

    /** Invalidates the active key; from then on the portal refuses the pipeline's configuration. */
    public PipelineKey revokeActiveKey(String reason) {
        PipelineKey active = activeKey()
                .orElseThrow(() -> new ConflictException("The pipeline has no active key to invalidate"));
        active.revoke(reason);
        return active;
    }

    public Optional<PipelineKey> activeKey() {
        return keys.stream().filter(PipelineKey::isActive).findFirst();
    }

    public boolean isEnabled() {
        return activeKey().isPresent();
    }

    /** The InfluxDB {@code project} tag the library writes for this pipeline. */
    public String influxProjectTag() {
        return type.influxProjectTag(service.getMetrics().influxProject());
    }

    public String influxEnv() {
        return service.getMetrics().influxEnv();
    }

    public Long getId() {
        return id;
    }

    public ServiceDefinition getService() {
        return service;
    }

    public PipelineType getType() {
        return type;
    }

    public PipelineSettings getSettings() {
        return settings;
    }

    public List<PipelineKey> getKeys() {
        return Collections.unmodifiableList(keys);
    }
}
