package com.bbh.itss.dso.portal.domain.evidence;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record EvidencePoint(String measurement, Map<String, String> values) {

    public EvidencePoint {
        Objects.requireNonNull(measurement, "a point belongs to a measurement");
        values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public String value(String name) {
        return values.get(name);
    }

    boolean isOf(String wanted) {
        return measurement.equals(wanted);
    }
}
