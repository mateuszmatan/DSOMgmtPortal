package com.bbh.itss.dso.portal.pipeline;

import com.bbh.itss.dso.portal.adapter.out.persistence.DelimitedListConverter;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
        agentLabels = Text.clean(agentLabels);
        extendedPipelineJob = Text.trimToNull(extendedPipelineJob);
        securityPipelineJob = Text.trimToNull(securityPipelineJob);
        jenkinsJob = Text.trimToNull(jenkinsJob);
        description = Text.trimToNull(description);
    }

    PipelineSettings forType(PipelineType type) {
        return new PipelineSettings(agentLabels, type == PipelineType.SECURITY ? extendedPipelineJob : null,
                type == PipelineType.EXTENDED ? securityPipelineJob : null, jenkinsJob, description);
    }

    public String jenkinsJobUrl(String jenkinsUrl) {
        return jobUrl(jenkinsJob, jenkinsUrl);
    }

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
