package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public enum RunResult {
    SUCCESS, UNSTABLE, FAILURE, ABORTED, NOT_BUILT, NO_DATA, DISABLED;

    private static final List<RunResult> SEVERITY = List.of(FAILURE, UNSTABLE, ABORTED, NOT_BUILT, SUCCESS, NO_DATA,
            DISABLED);

    public static RunResult fromTag(String tag) {
        if (tag == null) {
            return NO_DATA;
        }
        return switch (tag.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> SUCCESS;
            case "UNSTABLE" -> UNSTABLE;
            case "FAILURE" -> FAILURE;
            case "ABORTED" -> ABORTED;
            case "NOT_BUILT" -> NOT_BUILT;
            default -> NO_DATA;
        };
    }

    public static RunResult of(Pipeline pipeline, PipelineRun latestRun) {
        if (!pipeline.isEnabled()) {
            return DISABLED;
        }
        return latestRun == null ? NO_DATA : latestRun.result();
    }

    public static RunResult worst(Collection<RunResult> statuses) {
        return SEVERITY.stream().filter(statuses::contains).findFirst().orElse(NO_DATA);
    }
}
