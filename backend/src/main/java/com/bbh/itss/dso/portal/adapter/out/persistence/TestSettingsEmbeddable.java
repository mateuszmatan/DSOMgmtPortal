package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.TestSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record TestSettingsEmbeddable(
        @Column(name = "TESTS_MAX_PARALLEL") Integer maxParallel,
        @Column(name = "SMOKE_MAX_PARALLEL") Integer smokeMaxParallel,
        @Column(name = "REGRESSION_MAX_PARALLEL") Integer regressionMaxParallel,
        @Column(name = "PERFORMANCE_MAX_PARALLEL") Integer performanceMaxParallel) {

    static TestSettingsEmbeddable of(TestSettings tests) {
        return new TestSettingsEmbeddable(tests.maxParallel(), tests.smokeMaxParallel(), tests.regressionMaxParallel(),
                tests.performanceMaxParallel());
    }

    static TestSettings toDomain(TestSettingsEmbeddable tests) {
        return tests == null ? null : new TestSettings(tests.maxParallel, tests.smokeMaxParallel,
                tests.regressionMaxParallel, tests.performanceMaxParallel);
    }
}
