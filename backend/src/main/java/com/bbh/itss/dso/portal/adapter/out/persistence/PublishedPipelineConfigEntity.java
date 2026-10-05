package com.bbh.itss.dso.portal.adapter.out.persistence;

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
public class PublishedPipelineConfigEntity implements Persistable<Long> {

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

    protected PublishedPipelineConfigEntity() {
    }

    PublishedPipelineConfigEntity(Long pipelineId) {
        this.pipelineId = pipelineId;
    }

    void publish(String renderedJson, Instant now) {
        this.configJson = renderedJson;
        this.renderedAt = now;
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

    String getConfigJson() {
        return configJson;
    }

    Instant getRenderedAt() {
        return renderedAt;
    }
}
