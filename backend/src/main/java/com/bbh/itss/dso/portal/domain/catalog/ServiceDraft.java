package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ServiceDraft(Long id, String name, String description, ServiceSettings settings) {

    public ServiceDraft {
        name = name == null ? null : name.trim();
        description = trimToNull(description);
        Objects.requireNonNull(settings, "a service needs its settings");
    }

    Service place(int displayOrder, String productCode) {
        return new Service(id, name, description, displayOrder, settings.withDefaultMetricsProject(productCode, name));
    }
}
