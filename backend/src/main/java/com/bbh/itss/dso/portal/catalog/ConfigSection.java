package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.ValidationProblems;

public interface ConfigSection {

    void writeTo(ConfigTree config);

    default void validate(ValidationProblems problems) {
    }
}
