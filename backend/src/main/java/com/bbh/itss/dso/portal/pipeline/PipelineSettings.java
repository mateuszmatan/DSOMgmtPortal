package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What a pipeline needs besides its service: the agent labels offered by the AGENT_NAME parameter
 * ({@code agentNames}), the job the security pipeline starts when RUN_EXTENDED_PIPELINE is selected
 * ({@code jenkins.pipeline.extendedPipeline}), the security pipeline whose artifacts the extended pipeline copies
 * ({@code securityPipeline}) and the Jenkins job the pipeline runs in, which the portal links to.
 */
@Embeddable
public record PipelineSettings(
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "AGENT_LABELS", nullable = false, length = 1000)
        List<String> agentLabels,
        @Column(name = "EXTENDED_PIPELINE_JOB", length = 500)
        String extendedPipelineJob,
        @Column(name = "SECURITY_PIPELINE_JOB", length = 500)
        String securityPipelineJob,
        @Column(name = "JENKINS_JOB", length = 1000)
        String jenkinsJob,
        @Column(name = "DESCRIPTION", length = 1000)
        String description) {

    public PipelineSettings {
        agentLabels = DelimitedListConverter.clean(agentLabels);
        extendedPipelineJob = Text.trimToNull(extendedPipelineJob);
        securityPipelineJob = Text.trimToNull(securityPipelineJob);
        jenkinsJob = Text.trimToNull(jenkinsJob);
        description = Text.trimToNull(description);
    }

    /** Keeps only what applies to the pipeline type: each of the two linked jobs belongs to one type. */
    PipelineSettings forType(PipelineType type) {
        return new PipelineSettings(agentLabels, type == PipelineType.SECURITY ? extendedPipelineJob : null,
                type == PipelineType.EXTENDED ? securityPipelineJob : null, jenkinsJob, description);
    }

    /**
     * The address of the pipeline's Jenkins job: the job as entered when it is a URL, otherwise the job path
     * ({@code folder/job-name}) under the Jenkins URL of the global settings; null when neither is known.
     */
    public String jenkinsJobUrl(String jenkinsUrl) {
        return jobUrl(jenkinsJob, jenkinsUrl);
    }

    /**
     * The address of a Jenkins job given as a URL or as a path such as {@code DevSecOps/TARA/app-full}, which
     * becomes {@code <jenkinsUrl>/job/DevSecOps/job/TARA/job/app-full/}; null when it cannot be known.
     */
    public static String jobUrl(String job, String jenkinsUrl) {
        if (Text.isBlank(job)) {
            return null;
        }
        String trimmed = job.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (Text.isBlank(jenkinsUrl)) {
            return null;
        }
        String path = Arrays.stream(trimmed.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> "job/" + UriUtils.encodePathSegment(segment.trim(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        return jenkinsUrl.replaceAll("/+$", "") + "/" + path + "/";
    }
}
