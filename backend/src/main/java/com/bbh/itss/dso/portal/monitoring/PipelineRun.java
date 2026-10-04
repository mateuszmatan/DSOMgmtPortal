package com.bbh.itss.dso.portal.monitoring;

import java.time.Instant;
import java.util.Map;

/**
 * One run recorded in the {@code pipeline_run} measurement.
 */
public record PipelineRun(
        Instant time,
        RunResult result,
        String branch,
        Long build,
        Long durationSeconds,
        String commit,
        String job,
        Long stagesTotal,
        Long passed,
        Long warned,
        Long failed,
        Long blocked,
        Long skipped) {

    static PipelineRun fromRow(Map<String, String> row) {
        return new PipelineRun(Instant.parse(row.get("_time")), RunResult.fromTag(row.get("result")),
                emptyToNull(row.get("branch")), number(row.get("build")), number(row.get("duration_s")),
                emptyToNull(row.get("commit")), emptyToNull(row.get("job")), number(row.get("stages_total")),
                number(row.get("passed")), number(row.get("warned")), number(row.get("failed")),
                number(row.get("blocked")), number(row.get("skipped")));
    }

    static Long number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return (long) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
