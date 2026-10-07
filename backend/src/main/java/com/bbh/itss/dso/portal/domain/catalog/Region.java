package com.bbh.itss.dso.portal.domain.catalog;

import java.util.Locale;

public enum Region {
    RD, QC;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
