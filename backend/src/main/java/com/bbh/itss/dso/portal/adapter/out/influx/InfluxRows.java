package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;

import java.time.Instant;
import java.util.Map;

final class InfluxRows {

    private InfluxRows() {
    }

    static MetricsTag tag(Map<String, String> row) {
        return new MetricsTag(row.get("project"), row.get("env"));
    }

    static PipelineRun run(Map<String, String> row) {
        return new PipelineRun(Instant.parse(row.get("_time")), RunResult.fromTag(row.get("result")),
                text(row.get("branch")), number(row.get("build")), number(row.get("duration_s")),
                text(row.get("commit")), text(row.get("job")), number(row.get("stages_total")),
                number(row.get("passed")), number(row.get("warned")), number(row.get("failed")),
                number(row.get("blocked")), number(row.get("skipped")));
    }

    static DoraPoint doraPoint(Map<String, String> row) {
        if (row.get("_time") == null) {
            return null;
        }
        return new DoraPoint(Instant.parse(row.get("_time")), positive(row.get("deployment")),
                positive(row.get("change_failure")), orZero(number(row.get("lead_time_s"))),
                orZero(number(row.get("duration_s"))));
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

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean positive(String value) {
        Long number = number(value);
        return number != null && number > 0;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
