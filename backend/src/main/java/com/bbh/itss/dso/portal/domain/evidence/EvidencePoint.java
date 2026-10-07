package com.bbh.itss.dso.portal.domain.evidence;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

public record EvidencePoint(String measurement, Map<String, String> values) {

    public EvidencePoint {
        requireNonNull(measurement, "a point belongs to a measurement");
        values = unmodifiableMap(new HashMap<>(values));
    }

    public String value(String name) {
        return values.get(name);
    }

    boolean isOf(String wanted) {
        return measurement.equals(wanted);
    }
}
