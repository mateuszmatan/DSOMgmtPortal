package com.bbh.itss.dso.portal.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER;

    public String configValue() {
        return name().toLowerCase();
    }
}
