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

@Entity
@Table(name = "DSO_PIPELINE_CONFIG")
public class PublishedPipelineConfig implements Persistable<Long> {

    @Id
    @Column(name = "PIPELINE_ID")
    private Long pipelineId;

    @Lob
    @Column(name = "CONFIG_JSON", nullable = false)
    private String configJson;

    @Column(name = "RENDERED_AT", nullable = false)
    private Instant renderedAt;

    @Transient
    private boolean stored;

    protected PublishedPipelineConfig() {
    }

    PublishedPipelineConfig(Long pipelineId) {
        this.pipelineId = pipelineId;
    }

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
