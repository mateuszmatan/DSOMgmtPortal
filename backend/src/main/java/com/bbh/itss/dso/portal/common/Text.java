package com.bbh.itss.dso.portal.common;

public final class Text {

    private Text() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }
}
