package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.settings.port.in.UpdateGlobalSettingsCommand;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import com.bbh.itss.dso.portal.domain.settings.SeverityLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.EnumMap;
import java.util.Map;

public record GlobalSettingsRequest(
        Long version,
        @NotNull @Valid PlatformSettingsDto platform,
        @NotNull @Valid DeploymentDefaultsDto deployment,
        @NotNull Map<@NotNull Scanner, @NotNull @Valid SeverityLimitsDto> limits,
        @NotNull @Valid ScanSettingsDto scans,
        @NotNull @Valid ReleaseGateSettingsDto releaseGate,
        @NotNull @Valid ServiceDefaultsDto serviceDefaults,
        @NotNull @Valid GoldenFixPolicyDto goldenFix) {

    UpdateGlobalSettingsCommand toCommand() {
        return new UpdateGlobalSettingsCommand(version, values());
    }

    GlobalSettingsValues values() {
        Map<Scanner, SeverityLimits> domainLimits = new EnumMap<>(Scanner.class);
        limits.forEach((scanner, value) -> domainLimits.put(scanner, value.toDomain()));
        return new GlobalSettingsValues(platform.toDomain(), deployment.toDomain(), domainLimits, scans.toDomain(),
                releaseGate.toDomain(), serviceDefaults.toDomain(), goldenFix.toDomain());
    }
}
