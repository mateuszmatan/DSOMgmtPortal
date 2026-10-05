package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.util.List;

@Embeddable
public record PipelineSettingsEmbeddable(
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "AGENT_LABELS", nullable = false, length = 1000) List<String> agentLabels,
        @Column(name = "EXTENDED_PIPELINE_JOB", length = 500) String extendedPipelineJob,
        @Column(name = "SECURITY_PIPELINE_JOB", length = 500) String securityPipelineJob,
        @Column(name = "JENKINS_JOB", length = 1000) String jenkinsJob,
        @Column(name = "DESCRIPTION", length = 1000) String description) {

    static PipelineSettingsEmbeddable of(PipelineSettings settings) {
        return new PipelineSettingsEmbeddable(settings.agentLabels(), settings.extendedPipelineJob(),
                settings.securityPipelineJob(), settings.jenkinsJob(), settings.description());
    }

    PipelineSettings toDomain() {
        return new PipelineSettings(agentLabels, extendedPipelineJob, securityPipelineJob, jenkinsJob, description);
    }
}
