package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.Scanner;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public record GlobalSettingsResponse(
        long version,
        Instant updatedAt,
        PlatformSettingsDto platform,
        DeploymentDefaultsDto deployment,
        Map<Scanner, SeverityLimitsDto> limits,
        ScanSettingsDto scans,
        ReleaseGateSettingsDto releaseGate,
        ServiceDefaultsDto serviceDefaults,
        GoldenFixPolicyDto goldenFix) {

    static GlobalSettingsResponse from(GlobalSettings settings) {
        GlobalSettingsValues values = settings.values();
        Map<Scanner, SeverityLimitsDto> limits = new EnumMap<>(Scanner.class);
        values.limits().forEach((scanner, value) -> limits.put(scanner, SeverityLimitsDto.from(value)));
        return new GlobalSettingsResponse(settings.version(), settings.updatedAt(),
                PlatformSettingsDto.from(values.platform()), DeploymentDefaultsDto.from(values.deployment()), limits,
                ScanSettingsDto.from(values.scans()), ReleaseGateSettingsDto.from(values.releaseGate()),
                ServiceDefaultsDto.from(values.serviceDefaults()), GoldenFixPolicyDto.from(values.goldenFix()));
    }
}
