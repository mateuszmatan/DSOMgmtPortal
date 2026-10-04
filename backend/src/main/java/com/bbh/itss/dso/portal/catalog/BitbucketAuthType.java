package com.bbh.itss.dso.portal.catalog;

/**
 * How GoldenFix signs in to Bitbucket ({@code scm.bitbucket.authType}): a user name with a password or HTTP
 * access token, or a bearer HTTP access token.
 */
public enum BitbucketAuthType {
    BASIC, BEARER;

    public String configValue() {
        return name().toLowerCase();
    }
}
