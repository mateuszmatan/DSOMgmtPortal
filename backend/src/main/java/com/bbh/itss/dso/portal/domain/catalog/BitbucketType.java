package com.bbh.itss.dso.portal.domain.catalog;

public enum BitbucketType {
    SERVER, CLOUD;

    public String configValue() {
        return name().toLowerCase();
    }
}
