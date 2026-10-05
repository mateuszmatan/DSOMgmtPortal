package com.bbh.itss.dso.portal.domain.shared;

public interface ConfigSection {

    void writeTo(ConfigTree config);

    default void validate(ValidationProblems problems) {
    }
}
