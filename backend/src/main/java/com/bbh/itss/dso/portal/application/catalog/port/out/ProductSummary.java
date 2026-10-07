package com.bbh.itss.dso.portal.application.catalog.port.out;

import lombok.Builder;

import java.time.Instant;
import java.util.stream.Stream;

import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.Strings.CS;

@Builder
public record ProductSummary(long id, String code, String name, String description, String ownerTeam,
                             Long departmentId, String departmentName, Instant updatedAt) {

    public boolean matches(String search) {
        String needle = trimToEmpty(search).toLowerCase(ROOT);
        return needle.isEmpty() || Stream.of(name, code, ownerTeam, departmentName, description)
                .anyMatch(value -> CS.contains(lowerCase(value, ROOT), needle));
    }
}
