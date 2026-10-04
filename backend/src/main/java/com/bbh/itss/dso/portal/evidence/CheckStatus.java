package com.bbh.itss.dso.portal.evidence;

import java.util.Locale;

/**
 * The outcome of one check of a run (a stage, a scan, a test suite) as the DevSecOps library reports it,
 * plus NO_DATA when the run recorded nothing for the check.
 */
public enum CheckStatus {
    PASS, WARN, FAIL, BLOCKED, NOT_REQUIRED, SKIP, NO_DATA;

    static CheckStatus fromTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return NO_DATA;
        }
        try {
            return valueOf(tag.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NO_DATA;
        }
    }
}
