package com.bbh.itss.dso.portal.catalog;

/**
 * Whether a test job runs on the same Jenkins as the pipeline or on another one ({@code type} of a test job).
 */
public enum TestJobType {
    LOCAL, REMOTE;

    public String configValue() {
        return name().toLowerCase();
    }
}
