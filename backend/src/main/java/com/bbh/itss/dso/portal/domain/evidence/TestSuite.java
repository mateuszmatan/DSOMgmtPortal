package com.bbh.itss.dso.portal.domain.evidence;

import static java.util.Locale.ROOT;

public enum TestSuite {
    UNIT, SMOKE, REGRESSION, PERFORMANCE;

    String tag() {
        return name().toLowerCase(ROOT);
    }
}
