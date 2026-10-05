package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.Objects;

public record Service(Long id, String name, String description, int displayOrder, ServiceSettings settings) {

    public Service {
        name = name == null ? null : name.trim();
        description = Text.trimToNull(description);
        Objects.requireNonNull(settings, "a service needs its settings");
    }

    public boolean hasId(Long serviceId) {
        return id != null && id.equals(serviceId);
    }
}
