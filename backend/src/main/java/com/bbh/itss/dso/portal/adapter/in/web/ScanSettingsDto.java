package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.settings.ScanSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScanSettingsDto(
        @NotNull @Min(0) @Max(100) Integer coverageMinLine,
        @NotNull @Min(1) @Max(1440) Integer sastPrepareTimeoutMinutes,
        @NotNull @Min(1) @Max(1440) Integer sastPollTimeoutMinutes,
        @NotNull @Min(1) @Max(3600) Integer sastPollIntervalSeconds,
        @NotNull Boolean scaEnabled,
        @NotNull @Min(1) @Max(1440) Integer scaPollTimeoutMinutes,
        @NotNull @Min(1) @Max(3600) Integer scaPollIntervalSeconds,
        @NotNull @Min(1) @Max(1440) Integer dastPollTimeoutMinutes,
        @NotNull @Min(1) @Max(3600) Integer dastPollIntervalSeconds,
        @NotNull @Min(1) @Max(1440) Integer dastReportTimeoutMinutes,
        @NotNull @Min(1) @Max(3600) Integer dastReportIntervalSeconds,
        @NotNull Boolean sonarWaitForQualityGate,
        @NotNull @Min(1) @Max(1440) Integer sonarQualityGateTimeoutMinutes) {

    static ScanSettingsDto from(ScanSettings scans) {
        return new ScanSettingsDto(scans.coverageMinLine(), scans.sastPrepareTimeoutMinutes(),
                scans.sastPollTimeoutMinutes(), scans.sastPollIntervalSeconds(), scans.scaEnabled(),
                scans.scaPollTimeoutMinutes(), scans.scaPollIntervalSeconds(), scans.dastPollTimeoutMinutes(),
                scans.dastPollIntervalSeconds(), scans.dastReportTimeoutMinutes(), scans.dastReportIntervalSeconds(),
                scans.sonarWaitForQualityGate(), scans.sonarQualityGateTimeoutMinutes());
    }

    ScanSettings toDomain() {
        return new ScanSettings(coverageMinLine, sastPrepareTimeoutMinutes, sastPollTimeoutMinutes,
                sastPollIntervalSeconds, scaEnabled, scaPollTimeoutMinutes, scaPollIntervalSeconds,
                dastPollTimeoutMinutes, dastPollIntervalSeconds, dastReportTimeoutMinutes, dastReportIntervalSeconds,
                sonarWaitForQualityGate, sonarQualityGateTimeoutMinutes);
    }
}
