package com.bbh.itss.dso.portal.application.settings.port.in;

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;

public interface ManageGlobalSettingsUseCase {

    GlobalSettings ensureExists();

    GlobalSettings current();

    GlobalSettings update(UpdateGlobalSettingsCommand command);
}
