package com.bbh.itss.dso.portal.domain.catalog;

public enum TestStage {
    SMOKE, REGRESSION, PERFORMANCE;

    public String configKey() {
        return name().toLowerCase();
    }
}
