package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import java.util.List;

public record TestSettings(Integer maxParallel, Integer smokeMaxParallel, Integer regressionMaxParallel,
                           Integer performanceMaxParallel, Boolean smokeRequired, Boolean regressionRequired,
                           Boolean performanceRequired, Integer smokePollIntervalSec,
                           Integer regressionPollIntervalSec, Integer performancePollIntervalSec) {

    public static final TestSettings DEFAULTS = new TestSettings(null, null, null, null, true, true, true, null, null,
            null);

    public TestSettings {
        smokeRequired = !Boolean.FALSE.equals(smokeRequired);
        regressionRequired = !Boolean.FALSE.equals(regressionRequired);
        performanceRequired = !Boolean.FALSE.equals(performanceRequired);
    }

    public void writeTo(ConfigTree config, List<TestJob> jobs) {
        config.set("tests.maxParallel", maxParallel);
        for (TestStage stage : TestStage.values()) {
            String path = "tests." + stage.configKey();
            config.set(path + ".required", of(stage, smokeRequired, regressionRequired, performanceRequired) ? null : false)
                    .set(path + ".maxParallel", of(stage, smokeMaxParallel, regressionMaxParallel, performanceMaxParallel))
                    .set(path + ".pollIntervalSec",
                            of(stage, smokePollIntervalSec, regressionPollIntervalSec, performancePollIntervalSec))
                    .set(path + ".jobs", jobs.stream().filter(job -> job.stage() == stage).map(TestJob::toConfig).toList());
        }
    }

    private static <T> T of(TestStage stage, T smoke, T regression, T performance) {
        return switch (stage) {
            case SMOKE -> smoke;
            case REGRESSION -> regression;
            case PERFORMANCE -> performance;
        };
    }
}
