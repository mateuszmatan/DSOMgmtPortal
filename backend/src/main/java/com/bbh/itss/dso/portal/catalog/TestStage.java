package com.bbh.itss.dso.portal.catalog;

public enum TestStage {
    SMOKE, REGRESSION, PERFORMANCE;

    public String configKey() {
        return name().toLowerCase();
    }
}
