package com.bbh.itss.dso.portal.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * How many test jobs run at the same time: for every test stage ({@code tests.maxParallel}) and per stage
 * ({@code tests.<stage>.maxParallel}). Unset values fall back to the global default.
 */
@Embeddable
public record TestSettings(
        @Min(1) @Max(100)
        @Column(name = "TESTS_MAX_PARALLEL")
        Integer maxParallel,
        @Min(1) @Max(100)
        @Column(name = "SMOKE_MAX_PARALLEL")
        Integer smokeMaxParallel,
        @Min(1) @Max(100)
        @Column(name = "REGRESSION_MAX_PARALLEL")
        Integer regressionMaxParallel,
        @Min(1) @Max(100)
        @Column(name = "PERFORMANCE_MAX_PARALLEL")
        Integer performanceMaxParallel) {

    public static final TestSettings DEFAULTS = new TestSettings(null, null, null, null);

    /** Writes the limits and the jobs of each stage in the order they were entered. */
    public void writeTo(ConfigTree config, List<TestJob> jobs) {
        config.set("tests.maxParallel", maxParallel);
        for (TestStage stage : TestStage.values()) {
            String path = "tests." + stage.configKey();
            config.set(path + ".maxParallel", maxParallel(stage));
            config.set(path + ".jobs", jobs.stream().filter(job -> job.stage() == stage).map(TestJob::toConfig).toList());
        }
    }

    private Integer maxParallel(TestStage stage) {
        return switch (stage) {
            case SMOKE -> smokeMaxParallel;
            case REGRESSION -> regressionMaxParallel;
            case PERFORMANCE -> performanceMaxParallel;
        };
    }
}
