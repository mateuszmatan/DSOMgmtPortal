package com.bbh.itss.dso.portal.adapter.out.influx;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class Flux {

    private static final Pattern DURATION = Pattern.compile("^([1-9][0-9]*(ns|us|µs|ms|s|m|h|d|w|mo|y))+$");

    private Flux() {
    }

    static String string(String value) {
        if (value == null) {
            throw new IllegalArgumentException("a Flux string needs a value");
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("${", "\\${") + "\"";
    }

    static String strings(Collection<String> values) {
        return values.stream().distinct().map(Flux::string).collect(Collectors.joining(", "));
    }

    static String duration(String value) {
        if (value == null || !DURATION.matcher(value).matches()) {
            throw new IllegalArgumentException("'" + value + "' is not a Flux duration such as 365d");
        }
        return value;
    }

    static int positive(int value) {
        if (value < 1) {
            throw new IllegalArgumentException(value + " is not a positive number");
        }
        return value;
    }
}
