package com.bbh.itss.dso.portal.domain.evidence;

import java.util.Locale;

public enum TestSuite {
    UNIT, SMOKE, REGRESSION, PERFORMANCE;

    String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
