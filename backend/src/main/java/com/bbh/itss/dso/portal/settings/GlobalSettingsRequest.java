package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.GoldenFixPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * The global settings as edited in the portal, with the version they were read at so a concurrent change is
 * detected instead of overwritten.
 */
public record GlobalSettingsRequest(
        Long version,
        @NotNull @Valid PlatformSettings platform,
        @NotNull @Valid DeploymentDefaults deployment,
        @NotNull Map<@NotNull Scanner, @NotNull @Valid SeverityLimits> limits,
        @NotNull @Valid ScanSettings scans,
        @NotNull @Valid ReleaseGateSettings releaseGate,
        @NotNull @Valid ServiceDefaults serviceDefaults,
        @NotNull @Valid GoldenFixPolicy goldenFix) {

    GlobalSettingsValues values() {
        return new GlobalSettingsValues(platform, deployment, limits, scans, releaseGate, serviceDefaults, goldenFix);
    }
}
