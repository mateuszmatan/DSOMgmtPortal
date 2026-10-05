package com.bbh.itss.dso.portal.application.settings.port.in;

import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;

import java.util.Objects;

public record UpdateGlobalSettingsCommand(Long expectedVersion, GlobalSettingsValues values) {

    public UpdateGlobalSettingsCommand {
        Objects.requireNonNull(values, "values");
    }

    public static UpdateGlobalSettingsCommand unversioned(GlobalSettingsValues values) {
        return new UpdateGlobalSettingsCommand(null, values);
    }
}
