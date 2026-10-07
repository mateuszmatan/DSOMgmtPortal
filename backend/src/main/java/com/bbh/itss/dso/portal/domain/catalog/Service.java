package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record Service(Long id, String name, String description, int displayOrder, ServiceSettings settings) {

    public Service {
        name = name == null ? null : name.trim();
        description = trimToNull(description);
        Objects.requireNonNull(settings, "a service needs its settings");
    }

    public boolean hasId(Long serviceId) {
        return id != null && id.equals(serviceId);
    }
}
