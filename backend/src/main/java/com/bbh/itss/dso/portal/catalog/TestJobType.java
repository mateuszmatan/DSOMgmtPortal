package com.bbh.itss.dso.portal.catalog;

public enum TestJobType {
    LOCAL, REMOTE;

    public String configValue() {
        return name().toLowerCase();
    }
}
