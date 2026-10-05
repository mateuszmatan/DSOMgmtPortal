package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.settings.ReleaseGateSettings;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReleaseGateSettingsDto(
        @NotNull @Size(max = 4)
        List<@NotNull Scanner> scanners,
        @NotNull
        Boolean requireCoverage,
        @NotBlank @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "must be a file name such as release-gate.json")
        String stateFile) {

    public ReleaseGateSettingsDto {
        scanners = ReleaseGateSettings.normalize(scanners);
        stateFile = Text.trimToNull(stateFile);
    }

    static ReleaseGateSettingsDto from(ReleaseGateSettings releaseGate) {
        return new ReleaseGateSettingsDto(releaseGate.scanners(), releaseGate.requireCoverage(), releaseGate.stateFile());
    }

    ReleaseGateSettings toDomain() {
        return new ReleaseGateSettings(scanners, requireCoverage, stateFile);
    }
}
