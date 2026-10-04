package com.bbh.itss.dso.portal.catalog;

public enum BitbucketType {
    SERVER, CLOUD;

    public String configValue() {
        return name().toLowerCase();
    }
}
