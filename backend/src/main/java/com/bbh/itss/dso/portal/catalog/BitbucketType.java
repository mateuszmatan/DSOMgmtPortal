package com.bbh.itss.dso.portal.catalog;

/**
 * Bitbucket Data Center or Bitbucket Cloud ({@code scm.bitbucket.type}); the library detects it from the
 * repository URL when unset.
 */
public enum BitbucketType {
    SERVER, CLOUD;

    public String configValue() {
        return name().toLowerCase();
    }
}
