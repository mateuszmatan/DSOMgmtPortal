package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
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
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "DSO_PIPELINE")
public class PipelineEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SERVICE_ID", nullable = false)
    private ServiceEntity service;

    @Enumerated(EnumType.STRING)
    @Column(name = "PIPELINE_TYPE", nullable = false, length = 20)
    private PipelineType type;

    @Embedded
    private PipelineSettingsEmbeddable settings;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("issuedAt DESC, id DESC")
    private List<PipelineKeyEntity> keys = new ArrayList<>();

    protected PipelineEntity() {
    }

    PipelineEntity(ServiceEntity service, PipelineType type) {
        this.service = service;
        this.type = type;
    }

    Long getId() {
        return id;
    }

    ServiceEntity service() {
        return service;
    }

    PipelineType type() {
        return type;
    }

    PipelineSettingsEmbeddable settings() {
        return settings;
    }

    void settings(PipelineSettingsEmbeddable settings) {
        this.settings = settings;
    }

    List<PipelineKeyEntity> keys() {
        return List.copyOf(keys);
    }

    Optional<PipelineKeyEntity> key(Long keyId) {
        return keys.stream().filter(key -> keyId.equals(key.getId())).findFirst();
    }

    void addKey(PipelineKeyEntity key) {
        keys.addFirst(key);
    }
}
