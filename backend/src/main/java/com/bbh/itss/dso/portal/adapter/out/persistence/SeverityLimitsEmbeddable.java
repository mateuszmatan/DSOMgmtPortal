package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.SeverityLimits;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record SeverityLimitsEmbeddable(
        @Column(name = "MAX_CRITICAL", nullable = false) Integer maxCritical,
        @Column(name = "MAX_HIGH", nullable = false) Integer maxHigh,
        @Column(name = "MAX_MEDIUM", nullable = false) Integer maxMedium) {

    static SeverityLimitsEmbeddable of(SeverityLimits limits) {
        return new SeverityLimitsEmbeddable(limits.maxCritical(), limits.maxHigh(), limits.maxMedium());
    }

    SeverityLimits toDomain() {
        return new SeverityLimits(maxCritical, maxHigh, maxMedium);
    }
}
