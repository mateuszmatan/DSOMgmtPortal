package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.StoredList;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.UriEncoding;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record PipelineSettings(List<String> agentLabels, String extendedPipelineJob, String securityPipelineJob,
                               String jenkinsJob, String description) {

    public static final String DEFAULT_AGENT_LABEL = "linux-agent";

    public PipelineSettings {
        agentLabels = Text.clean(agentLabels);
        extendedPipelineJob = trimToNull(extendedPipelineJob);
        securityPipelineJob = trimToNull(securityPipelineJob);
        jenkinsJob = trimToNull(jenkinsJob);
        description = trimToNull(description);
    }

    public static PipelineSettings forNewService() {
        return new PipelineSettings(List.of(DEFAULT_AGENT_LABEL), null, null, null, null);
    }

    public void validate(ValidationProblems problems) {
        problems.require("agentLabels", agentLabels, "add at least one Jenkins agent label");
        StoredList.COMMAS_1000.check(problems, "agentLabels", agentLabels);
    }

    public PipelineSettings forType(PipelineType type) {
        return new PipelineSettings(agentLabels, type == PipelineType.SECURITY ? extendedPipelineJob : null,
                type == PipelineType.EXTENDED ? securityPipelineJob : null, jenkinsJob, description);
    }

    public String jenkinsJobUrl(String jenkinsUrl) {
        return jobUrl(jenkinsJob, jenkinsUrl);
    }

    public boolean builds(String recordedJob) {
        String path = jobPath();
        return path != null && recordedJob != null
                && (recordedJob.equals(path) || recordedJob.startsWith(path + "/"));
    }

    public String jobPath() {
        if (jenkinsJob == null) {
            return null;
        }
        if (!Text.isUrl(jenkinsJob)) {
            return segments(jenkinsJob).collect(Collectors.joining("/"));
        }
        String[] parts = jenkinsJob.replaceFirst("^https?://[^/]*", "").replaceFirst("[?#].*$", "").split("/");
        List<String> names = new ArrayList<>();
        for (int i = 0; i + 1 < parts.length; i++) {
            if (parts[i].equals("job")) {
                names.add(UriEncoding.decode(parts[++i]));
            }
        }
        return names.isEmpty() ? null : String.join("/", names);
    }

    public static String jobUrl(String job, String jenkinsUrl) {
        if (isBlank(job)) {
            return null;
        }
        String trimmed = job.trim();
        if (Text.isUrl(trimmed)) {
            return trimmed;
        }
        if (isBlank(jenkinsUrl)) {
            return null;
        }
        String path = segments(trimmed)
                .map(segment -> "job/" + UriEncoding.pathSegment(segment))
                .collect(Collectors.joining("/"));
        return stripEnd(trim(jenkinsUrl), "/") + "/" + path + "/";
    }

    private static Stream<String> segments(String path) {
        return Arrays.stream(path.split("/")).map(String::trim).filter(segment -> !segment.isEmpty());
    }
}
