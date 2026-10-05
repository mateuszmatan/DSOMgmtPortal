package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.shared.DomainException;

public class MetricsUnavailableException extends DomainException {

    public MetricsUnavailableException(String reason) {
        super(reason);
    }
}
