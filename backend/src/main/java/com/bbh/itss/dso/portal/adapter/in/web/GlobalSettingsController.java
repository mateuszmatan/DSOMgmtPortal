package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class GlobalSettingsController {

    private final ManageGlobalSettingsUseCase settings;

    public GlobalSettingsController(ManageGlobalSettingsUseCase settings) {
        this.settings = settings;
    }

    @GetMapping
    public GlobalSettingsResponse get() {
        return GlobalSettingsResponse.from(settings.current());
    }

    @PutMapping
    public GlobalSettingsResponse update(@Valid @RequestBody GlobalSettingsRequest request) {
        return GlobalSettingsResponse.from(settings.update(request.toCommand()));
    }
}
