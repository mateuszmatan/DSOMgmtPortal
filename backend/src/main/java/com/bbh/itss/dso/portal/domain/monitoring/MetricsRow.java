package com.bbh.itss.dso.portal.domain.monitoring;

import java.time.Instant;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public final class MetricsRow {

    private MetricsRow() {
    }

    public static MetricsTag tag(Map<String, String> row) {
        return new MetricsTag(row.get("project"), row.get("env"));
    }

    public static PipelineRun run(Map<String, String> row) {
        return new PipelineRun(Instant.parse(row.get("_time")), RunResult.fromTag(row.get("result")),
                trimToNull(row.get("branch")), number(row.get("build")), number(row.get("duration_s")),
                trimToNull(row.get("commit")), trimToNull(row.get("job")), number(row.get("stages_total")),
                number(row.get("passed")), number(row.get("warned")), number(row.get("failed")),
                number(row.get("blocked")), number(row.get("skipped")));
    }

    public static DoraPoint doraPoint(Map<String, String> row) {
        if (row.get("_time") == null) {
            return null;
        }
        return new DoraPoint(Instant.parse(row.get("_time")), positive(row.get("deployment")),
                positive(row.get("change_failure")), orZero(number(row.get("lead_time_s"))),
                orZero(number(row.get("duration_s"))));
    }

    public static Long number(String value) {
        Double decimal = decimal(value);
        return decimal == null ? null : (long) Math.floor(decimal);
    }

    public static Double decimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean positive(String value) {
        Long number = number(value);
        return number != null && number > 0;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
