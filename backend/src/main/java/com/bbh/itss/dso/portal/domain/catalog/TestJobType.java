package com.bbh.itss.dso.portal.domain.catalog;

public enum TestJobType {
    LOCAL, REMOTE;

    public String configValue() {
        return name().toLowerCase();
    }
}
