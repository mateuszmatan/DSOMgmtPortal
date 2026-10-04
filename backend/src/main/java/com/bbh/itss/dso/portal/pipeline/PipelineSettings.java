package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.util.List;

/**
 * What the Jenkinsfile passes to a pipeline today besides the project names: the agent labels offered by
 * the AGENT_NAME parameter ({@code agentNames}) and, for the security pipeline, the job it starts when
 * RUN_EXTENDED_PIPELINE is selected ({@code jenkins.pipeline.extendedPipeline}).
 */
@Embeddable
public record PipelineSettings(
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "AGENT_LABELS", nullable = false, length = 1000)
        List<String> agentLabels,
        @Column(name = "EXTENDED_PIPELINE_JOB", length = 500)
        String extendedPipelineJob,
        @Column(name = "DESCRIPTION", length = 1000)
        String description) {

    public PipelineSettings {
        agentLabels = DelimitedListConverter.clean(agentLabels);
        extendedPipelineJob = Text.trimToNull(extendedPipelineJob);
        description = Text.trimToNull(description);
    }

    /** Keeps only what applies to the pipeline type: the extended pipeline job belongs to the security pipeline. */
    PipelineSettings forType(PipelineType type) {
        return type == PipelineType.SECURITY ? this : new PipelineSettings(agentLabels, null, description);
    }
}
