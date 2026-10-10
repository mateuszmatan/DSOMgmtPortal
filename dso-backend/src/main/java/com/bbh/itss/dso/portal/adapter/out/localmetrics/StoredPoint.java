package com.bbh.itss.dso.portal.adapter.out.localmetrics;

import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

record StoredPoint(String measurement, MetricsTag tag, String job, Instant time, Map<String, String> values) {

    StoredPoint {
        values = Map.copyOf(values);
    }

    Map<String, String> row() {
        Map<String, String> row = new HashMap<>(values);
        row.put("_measurement", measurement);
        row.put("_time", time.toString());
        row.put("project", tag.project());
        row.put("env", tag.env());
        if (job != null) {
            row.put("job", job);
        }
        return row;
    }
}
