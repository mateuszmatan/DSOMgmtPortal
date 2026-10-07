package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Locale;

public enum TestStage {
    SMOKE, REGRESSION, PERFORMANCE;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
