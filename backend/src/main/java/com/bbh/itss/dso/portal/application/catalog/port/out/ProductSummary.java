package com.bbh.itss.dso.portal.application.catalog.port.out;

import java.time.Instant;
import java.util.Locale;
import java.util.stream.Stream;

public record ProductSummary(long id, String code, String name, String description, String ownerTeam,
                             Instant updatedAt) {

    public boolean matches(String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || Stream.of(name, code, ownerTeam, description)
                .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(needle));
    }
}
