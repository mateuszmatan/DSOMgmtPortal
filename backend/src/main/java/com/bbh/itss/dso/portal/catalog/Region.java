package com.bbh.itss.dso.portal.catalog;

public enum Region {
    RD, QC;

    public String configKey() {
        return name().toLowerCase();
    }
}
