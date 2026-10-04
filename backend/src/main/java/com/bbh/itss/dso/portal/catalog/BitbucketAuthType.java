package com.bbh.itss.dso.portal.catalog;

public enum BitbucketAuthType {
    BASIC, BEARER;

    public String configValue() {
        return name().toLowerCase();
    }
}
