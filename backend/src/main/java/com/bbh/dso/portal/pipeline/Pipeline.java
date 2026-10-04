package com.bbh.dso.portal.pipeline;

import com.bbh.dso.portal.catalog.ServiceDefinition;
import com.bbh.dso.portal.common.AuditedEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A DevSecOps pipeline of one service. The Jenkins job identifies itself with the pipeline's active key
 * and everything else is read from the portal.
 */
@Entity
@Table(name = "DSO_PIPELINE")
public class Pipeline extends AuditedEntity {

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

    /** Jenkins agent labels offered by the AGENT_NAME parameter ({@code agentNames}), comma separated. */
    @Column(name = "AGENT_LABELS", nullable = false, length = 1000)
    private String agentLabels;

    /** Job started by the security pipeline when RUN_EXTENDED_PIPELINE is selected. */
    @Column(name = "EXTENDED_PIPELINE_JOB", length = 500)
    private String extendedPipelineJob;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("issuedAt DESC")
    private List<PipelineKey> keys = new ArrayList<>();

    protected Pipeline() {
    }

    public Pipeline(ServiceDefinition service, PipelineType type) {
        this.service = service;
        this.type = type;
    }

    public Optional<PipelineKey> activeKey() {
        return keys.stream().filter(PipelineKey::isActive).findFirst();
    }

    void addKey(PipelineKey key) {
        keys.addFirst(key);
    }

    public List<String> agentLabelList() {
        return Arrays.stream(agentLabels.split(",")).map(String::trim).filter(label -> !label.isEmpty()).toList();
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

    public String getAgentLabels() {
        return agentLabels;
    }

    public void setAgentLabels(String agentLabels) {
        this.agentLabels = agentLabels;
    }

    public String getExtendedPipelineJob() {
        return extendedPipelineJob;
    }

    public void setExtendedPipelineJob(String extendedPipelineJob) {
        this.extendedPipelineJob = extendedPipelineJob;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PipelineKey> getKeys() {
        return keys;
    }
}
