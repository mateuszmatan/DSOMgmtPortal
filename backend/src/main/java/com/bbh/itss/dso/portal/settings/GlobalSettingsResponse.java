package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.GoldenFixPolicy;

import java.time.Instant;
import java.util.Map;

public record GlobalSettingsResponse(
        long version,
        Instant updatedAt,
        PlatformSettings platform,
        DeploymentDefaults deployment,
        Map<Scanner, SeverityLimits> limits,
        ScanSettings scans,
        ReleaseGateSettings releaseGate,
        ServiceDefaults serviceDefaults,
        GoldenFixPolicy goldenFix) {

    static GlobalSettingsResponse from(GlobalSettings settings) {
        GlobalSettingsValues v = settings.values();
        return new GlobalSettingsResponse(settings.getVersion(), settings.getUpdatedAt(), v.platform(), v.deployment(),
                v.limits(), v.scans(), v.releaseGate(), v.serviceDefaults(), v.goldenFix());
    }
}
