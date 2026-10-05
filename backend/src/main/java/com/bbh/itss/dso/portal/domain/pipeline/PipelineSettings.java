package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.Text;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record PipelineSettings(
        List<String> agentLabels,
        String extendedPipelineJob,
        String securityPipelineJob,
        String jenkinsJob,
        String description) {

    private static final String PATH_SEGMENT_SYMBOLS = "-._~!$&'()*+,;=:@";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public PipelineSettings {
        agentLabels = Text.clean(agentLabels);
        extendedPipelineJob = Text.trimToNull(extendedPipelineJob);
        securityPipelineJob = Text.trimToNull(securityPipelineJob);
        jenkinsJob = Text.trimToNull(jenkinsJob);
        description = Text.trimToNull(description);
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
                .map(segment -> "job/" + encodePathSegment(segment.trim()))
                .collect(Collectors.joining("/"));
        return jenkinsUrl.replaceAll("/+$", "") + "/" + path + "/";
    }

    static String encodePathSegment(String segment) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (isPathSegmentCharacter(c)) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
            }
        }
        return encoded.toString();
    }

    private static boolean isPathSegmentCharacter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || (c < 0x80 && PATH_SEGMENT_SYMBOLS.indexOf(c) >= 0);
    }
}
