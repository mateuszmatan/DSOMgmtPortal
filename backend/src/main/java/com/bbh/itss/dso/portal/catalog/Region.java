package com.bbh.itss.dso.portal.catalog;

/**
 * The test environments a service is deployed to: the lower test region (RD) and the higher one (QC).
 */
public enum Region {
    RD, QC;

    public String configKey() {
        return name().toLowerCase();
    }
}
