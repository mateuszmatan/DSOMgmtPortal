package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.StoredList;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.UriEncoding;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record PipelineSettings(
        List<String> agentLabels,
        String extendedPipelineJob,
        String securityPipelineJob,
        String jenkinsJob,
        String description) {

    public PipelineSettings {
        agentLabels = Text.clean(agentLabels);
        extendedPipelineJob = Text.trimToNull(extendedPipelineJob);
        securityPipelineJob = Text.trimToNull(securityPipelineJob);
        jenkinsJob = Text.trimToNull(jenkinsJob);
        description = Text.trimToNull(description);
    }

    public void validate(ValidationProblems problems) {
        StoredList.COMMAS_1000.check(problems, "agentLabels", agentLabels);
    }

    public PipelineSettings forType(PipelineType type) {
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
                .map(segment -> "job/" + UriEncoding.pathSegment(segment.trim()))
                .collect(Collectors.joining("/"));
        return jenkinsUrl.replaceAll("/+$", "") + "/" + path + "/";
    }
}
