package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import java.util.List;

public record TestSettings(Integer maxParallel, Integer smokeMaxParallel, Integer regressionMaxParallel,
                           Integer performanceMaxParallel) {

    public static final TestSettings DEFAULTS = new TestSettings(null, null, null, null);

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
