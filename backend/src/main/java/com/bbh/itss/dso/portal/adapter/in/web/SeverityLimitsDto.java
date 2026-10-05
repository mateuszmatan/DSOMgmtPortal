package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.settings.SeverityLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SeverityLimitsDto(
        @NotNull @Min(0) @Max(100_000) Integer maxCritical,
        @NotNull @Min(0) @Max(100_000) Integer maxHigh,
        @NotNull @Min(0) @Max(100_000) Integer maxMedium) {

    static SeverityLimitsDto from(SeverityLimits limits) {
        return new SeverityLimitsDto(limits.maxCritical(), limits.maxHigh(), limits.maxMedium());
    }

    SeverityLimits toDomain() {
        return new SeverityLimits(maxCritical, maxHigh, maxMedium);
    }
}
