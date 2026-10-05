package com.bbh.itss.dso.portal.domain.settings;

public enum Scanner {
    SAST("sast", "sast"),
    SCA("sca", "sca"),
    NEXUS_IQ("niq", "tools.nexusIq"),
    DAST("dast", "dast");

    private final String gateKey;
    private final String defaultsPath;

    Scanner(String gateKey, String defaultsPath) {
        this.gateKey = gateKey;
        this.defaultsPath = defaultsPath;
    }

    public String gateKey() {
        return gateKey;
    }

    public String defaultsPath() {
        return defaultsPath;
    }
}
