package com.bbh.itss.dso.portal.catalog;

/**
 * Deployment targets the DevSecOps library supports ({@code deployTarget} in config.yaml).
 */
public enum DeployTarget {
    VM, OPENSHIFT;

    public String configValue() {
        return name().toLowerCase();
    }
}
