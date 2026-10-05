package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;

public record ServiceCommand(Long id, String name, String description, ServiceSettings settings) {

    ServiceDraft toDraft() {
        return new ServiceDraft(id, name, description, settings);
    }
}
