package com.bbh.itss.dso.portal.domain.catalog;

public enum Region {
    RD, QC;

    public String configKey() {
        return name().toLowerCase();
    }
}
