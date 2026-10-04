package com.bbh.itss.dso.portal.evidence;

import java.util.Locale;

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
