package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.ConfigTree;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Embeddable
public record SeverityLimits(
        @NotNull @Min(0) @Max(100_000)
        @Column(name = "MAX_CRITICAL", nullable = false)
        Integer maxCritical,
        @NotNull @Min(0) @Max(100_000)
        @Column(name = "MAX_HIGH", nullable = false)
        Integer maxHigh,
        @NotNull @Min(0) @Max(100_000)
        @Column(name = "MAX_MEDIUM", nullable = false)
        Integer maxMedium) {

    public static final SeverityLimits ZERO = new SeverityLimits(0, 0, 0);

    public void writeTo(ConfigTree config, String path) {
        config.set(path + ".maxCritical", maxCritical)
                .set(path + ".maxHigh", maxHigh)
                .set(path + ".maxMedium", maxMedium);
    }
}
