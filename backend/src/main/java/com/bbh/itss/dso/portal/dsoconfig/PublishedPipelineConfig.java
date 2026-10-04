package com.bbh.itss.dso.portal.dsoconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * A pipeline's complete configuration as JSON, kept up to date by {@link PipelineConfigPublisher}. The view
 * {@code DSO_LIBRARY_CONFIG_V} joins it with the pipeline's keys, so the DevSecOps library can read its
 * configuration by key with a read-only database account and nothing else.
 */
@Entity
@Table(name = "DSO_PIPELINE_CONFIG")
public class PublishedPipelineConfig implements Persistable<Long> {

    @Id
    @Column(name = "PIPELINE_ID")
    private Long pipelineId;

    // The length makes MySQL expect LONGTEXT, as created by the changelog; Oracle and H2 store a CLOB.
    @Lob
    @Column(name = "CONFIG_JSON", nullable = false, length = Integer.MAX_VALUE)
    private String configJson;

    @Column(name = "RENDERED_AT", nullable = false)
    private Instant renderedAt;

    /** The key is the pipeline's, so saving a new row must insert it rather than merge it. */
    @Transient
    private boolean stored;

    protected PublishedPipelineConfig() {
    }

    PublishedPipelineConfig(Long pipelineId) {
        this.pipelineId = pipelineId;
    }

    /** Takes the newly rendered configuration; returns false, and keeps the time, when it is unchanged. */
    boolean publish(String renderedJson, Instant now) {
        if (renderedJson.equals(configJson)) {
            return false;
        }
        this.configJson = renderedJson;
        this.renderedAt = now;
        return true;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        stored = true;
    }

    @Override
    public Long getId() {
        return pipelineId;
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    public Long getPipelineId() {
        return pipelineId;
    }

    public String getConfigJson() {
        return configJson;
    }

    public Instant getRenderedAt() {
        return renderedAt;
    }
}
