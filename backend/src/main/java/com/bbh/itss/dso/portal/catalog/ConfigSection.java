package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.ValidationProblems;

/**
 * A group of settings that knows how it is written in the config.yaml of the DevSecOps library and which
 * rules of the library it has to satisfy.
 */
public interface ConfigSection {

    /** Writes the settings into a project entry of config.yaml. */
    void writeTo(ConfigTree config);

    /** Reports violations of the library's rules; field names are relative to the section. */
    default void validate(ValidationProblems problems) {
    }
}
