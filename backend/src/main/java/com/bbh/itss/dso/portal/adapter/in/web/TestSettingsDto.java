package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.TestSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TestSettingsDto(
        @Min(1) @Max(100)
        Integer maxParallel,
        @Min(1) @Max(100)
        Integer smokeMaxParallel,
        @Min(1) @Max(100)
        Integer regressionMaxParallel,
        @Min(1) @Max(100)
        Integer performanceMaxParallel) {

    static TestSettingsDto from(TestSettings source) {
        return new TestSettingsDto(source.maxParallel(), source.smokeMaxParallel(), source.regressionMaxParallel(),
                source.performanceMaxParallel());
    }

    TestSettings toDomain() {
        return new TestSettings(maxParallel, smokeMaxParallel, regressionMaxParallel, performanceMaxParallel);
    }
}
