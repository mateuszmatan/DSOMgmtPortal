package com.bbh.itss.dso.portal.domain.evidence;

import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.EnumUtils.getEnum;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.upperCase;

public enum CheckStatus {
    PASS, WARN, FAIL, BLOCKED, NOT_REQUIRED, SKIP, NO_DATA;

    public static CheckStatus fromTag(String tag) {
        return getEnum(CheckStatus.class, upperCase(trim(tag), ROOT), NO_DATA);
    }
}
