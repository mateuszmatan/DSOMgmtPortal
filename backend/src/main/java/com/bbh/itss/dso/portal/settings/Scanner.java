package com.bbh.itss.dso.portal.settings;

/**
 * The security scanners the BBH policy sets limits for. {@code gateKey} is the name the release gate uses and
 * {@code defaultsPath} the section of the library defaults that holds the scanner's limits.
 */
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
