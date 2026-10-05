package com.bbh.itss.dso.portal.domain.settings;

public class MissingGlobalSettingsException extends IllegalStateException {

    public MissingGlobalSettingsException() {
        super("The global settings are missing; the portal creates them at start-up");
    }
}
