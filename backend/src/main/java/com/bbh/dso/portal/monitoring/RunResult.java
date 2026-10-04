package com.bbh.dso.portal.monitoring;

/**
 * Outcome of a pipeline as the monitoring pages show it. The first five mirror the Jenkins build result the
 * library writes to the {@code result} tag; NO_DATA means no run was recorded and DISABLED that the
 * pipeline has no active key.
 */
public enum RunResult {
    SUCCESS, UNSTABLE, FAILURE, ABORTED, NOT_BUILT, NO_DATA, DISABLED;

    static RunResult fromTag(String tag) {
        if (tag == null) {
            return NO_DATA;
        }
        return switch (tag.trim().toUpperCase()) {
            case "SUCCESS" -> SUCCESS;
            case "UNSTABLE" -> UNSTABLE;
            case "FAILURE" -> FAILURE;
            case "ABORTED" -> ABORTED;
            case "NOT_BUILT" -> NOT_BUILT;
            default -> NO_DATA;
        };
    }
}
