package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
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

    Pipeline toDomain() {
        ServiceRef ref = new ServiceRef(service.product().getId(), service.getId());
        return Pipeline.restore(id, ref, type, RecordMapper.map(settings, PipelineSettings.class),
                keys.stream().map(PipelineKeyEntity::toDomain).toList(), getVersion(), getCreatedAt(), getUpdatedAt());
    }

    void apply(Pipeline pipeline) {
        settings = RecordMapper.map(pipeline.settings(), PipelineSettingsEmbeddable.class);
        pipeline.keys().stream().filter(key -> key.id() != null)
                .forEach(key -> keys.stream().filter(entity -> key.id().equals(entity.getId())).findFirst()
                        .orElseThrow().state(key.status(), key.revokedAt(), key.revokeReason()));
    }

    void addIssuedKeys(Pipeline pipeline) {
        List<PipelineKey> issued = pipeline.keys();
        for (int index = issued.size() - 1; index >= 0; index--) {
            PipelineKey key = issued.get(index);
            if (key.id() == null) {
                PipelineKeyEntity added = new PipelineKeyEntity(this, key.value(), key.issuedAt());
                added.state(key.status(), key.revokedAt(), key.revokeReason());
                keys.addFirst(added);
            }
        }
    }

    @Embeddable
    public record PipelineSettingsEmbeddable(
            @Convert(converter = DelimitedListConverter.Commas.class)
            @Column(name = "AGENT_LABELS", nullable = false, length = 1000) List<String> agentLabels,
            @Column(name = "EXTENDED_PIPELINE_JOB", length = 500) String extendedPipelineJob,
            @Column(name = "SECURITY_PIPELINE_JOB", length = 500) String securityPipelineJob,
            @Column(name = "JENKINS_JOB", length = 1000) String jenkinsJob,
            @Column(name = "DESCRIPTION", length = 1000) String description) {
    }
}
