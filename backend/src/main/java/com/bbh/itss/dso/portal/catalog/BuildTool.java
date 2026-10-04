package com.bbh.itss.dso.portal.catalog;

/**
 * Build systems the DevSecOps library supports ({@code buildTool} in config.yaml).
 */
public enum BuildTool {
    GRADLE, MAVEN, FLUTTER;

    public String configValue() {
        return name().toLowerCase();
    }
}
