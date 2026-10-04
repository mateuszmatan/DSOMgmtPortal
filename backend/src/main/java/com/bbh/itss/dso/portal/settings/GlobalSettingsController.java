package com.bbh.itss.dso.portal.settings;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class GlobalSettingsController {

    private final GlobalSettingsService settings;

    public GlobalSettingsController(GlobalSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public GlobalSettingsResponse get() {
        return settings.get();
    }

    @PutMapping
    public GlobalSettingsResponse update(@Valid @RequestBody GlobalSettingsRequest request) {
        return settings.update(request);
    }
}
