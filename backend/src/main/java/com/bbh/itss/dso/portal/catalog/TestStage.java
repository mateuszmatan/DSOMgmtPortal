package com.bbh.itss.dso.portal.catalog;

/**
 * The test stages that trigger Jenkins test jobs ({@code tests.smoke}, {@code tests.regression},
 * {@code tests.performance}).
 */
public enum TestStage {
    SMOKE, REGRESSION, PERFORMANCE;

    public String configKey() {
        return name().toLowerCase();
    }
}
