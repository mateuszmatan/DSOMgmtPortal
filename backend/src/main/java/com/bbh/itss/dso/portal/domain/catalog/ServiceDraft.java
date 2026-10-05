package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.Objects;

public record ServiceDraft(Long id, String name, String description, ServiceSettings settings) {

    public ServiceDraft {
        name = name == null ? null : name.trim();
        description = Text.trimToNull(description);
        Objects.requireNonNull(settings, "a service needs its settings");
    }

    Service place(int displayOrder, String productCode) {
        return new Service(id, name, description, displayOrder, settings.withDefaultMetricsProject(productCode, name));
    }
}
