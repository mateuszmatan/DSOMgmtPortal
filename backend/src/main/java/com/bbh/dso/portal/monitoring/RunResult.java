package com.bbh.dso.portal.monitoring;

import com.bbh.dso.portal.pipeline.Pipeline;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Outcome of a pipeline as the monitoring pages show it. The first five mirror the Jenkins build result the
 * library writes to the {@code result} tag; NO_DATA means no run was recorded and DISABLED that the pipeline
 * has no active key.
 */
public enum RunResult {
    SUCCESS, UNSTABLE, FAILURE, ABORTED, NOT_BUILT, NO_DATA, DISABLED;

    /** Order of severity used to summarise several pipelines in one status. */
    private static final List<RunResult> SEVERITY = List.of(FAILURE, UNSTABLE, ABORTED, NOT_BUILT, SUCCESS, NO_DATA,
            DISABLED);

    static RunResult fromTag(String tag) {
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

    /** The status of a pipeline given its latest run, which may be missing. */
    static RunResult of(Pipeline pipeline, PipelineRun latestRun) {
        if (!pipeline.isEnabled()) {
            return DISABLED;
        }
        return latestRun == null ? NO_DATA : latestRun.result();
    }

    /** The most severe of the statuses; NO_DATA when there are none. */
    static RunResult worst(Collection<RunResult> statuses) {
        return SEVERITY.stream().filter(statuses::contains).findFirst().orElse(NO_DATA);
    }
}
