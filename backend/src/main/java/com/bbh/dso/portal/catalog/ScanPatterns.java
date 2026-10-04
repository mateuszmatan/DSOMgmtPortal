package com.bbh.dso.portal.catalog;

import java.util.Arrays;
import java.util.List;

/**
 * Nexus IQ scan patterns are stored in one column, one Ant pattern per line.
 */
public final class ScanPatterns {

    private ScanPatterns() {
    }

    public static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split("\n")).map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    static String join(List<String> patterns) {
        if (patterns == null) {
            return null;
        }
        List<String> cleaned = patterns.stream().map(String::trim).filter(p -> !p.isEmpty()).distinct().toList();
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }
}
