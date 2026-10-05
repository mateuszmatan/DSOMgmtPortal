package com.bbh.itss.dso.portal.domain.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER;

    public String configValue() {
        return name().toLowerCase();
    }
}
