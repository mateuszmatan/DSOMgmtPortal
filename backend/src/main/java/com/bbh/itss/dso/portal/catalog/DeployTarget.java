package com.bbh.itss.dso.portal.catalog;

public enum DeployTarget {
    VM, OPENSHIFT;

    public String configValue() {
        return name().toLowerCase();
    }
}
