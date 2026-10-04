package com.bbh.itss.dso.portal.dsoconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A pipeline's complete configuration as JSON, kept up to date by {@link PipelineConfigPublisher}. The view
 * {@code DSO_LIBRARY_CONFIG_V} joins it with the pipeline's keys, so the DevSecOps library can read its
 * configuration by key with a read-only database account and nothing else.
 */
@Entity
@Table(name = "DSO_PIPELINE_CONFIG")
public class PublishedPipelineConfig {

    @Id
    @Column(name = "PIPELINE_ID")
    private Long pipelineId;

    @Lob
    @Column(name = "CONFIG_JSON", nullable = false)
    private String configJson;

    @Column(name = "RENDERED_AT", nullable = false)
    private Instant renderedAt;

    protected PublishedPipelineConfig() {
    }

    PublishedPipelineConfig(Long pipelineId) {
        this.pipelineId = pipelineId;
    }

    void publish(String configJson, Instant renderedAt) {
        this.configJson = configJson;
        this.renderedAt = renderedAt;
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
