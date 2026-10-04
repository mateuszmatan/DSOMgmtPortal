package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.ConfigTree;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.type.NumericBooleanConverter;

/**
 * BBH policy no service can change: the required line coverage, how long the library waits for each scanner and
 * how often it polls, whether the SCA findings count, and whether the build waits for the SonarQube quality gate.
 */
@Embeddable
public record ScanSettings(
        @NotNull @Min(0) @Max(100)
        @Column(name = "COVERAGE_MIN_LINE", nullable = false)
        Integer coverageMinLine,
        @NotNull @Min(1) @Max(1440)
        @Column(name = "SAST_PREPARE_TIMEOUT_MIN", nullable = false)
        Integer sastPrepareTimeoutMinutes,
        @NotNull @Min(1) @Max(1440)
        @Column(name = "SAST_POLL_TIMEOUT_MIN", nullable = false)
        Integer sastPollTimeoutMinutes,
        @NotNull @Min(1) @Max(3600)
        @Column(name = "SAST_POLL_INTERVAL_SEC", nullable = false)
        Integer sastPollIntervalSeconds,
        @NotNull
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SCA_ENABLED", nullable = false)
        Boolean scaEnabled,
        @NotNull @Min(1) @Max(1440)
        @Column(name = "SCA_POLL_TIMEOUT_MIN", nullable = false)
        Integer scaPollTimeoutMinutes,
        @NotNull @Min(1) @Max(3600)
        @Column(name = "SCA_POLL_INTERVAL_SEC", nullable = false)
        Integer scaPollIntervalSeconds,
        @NotNull @Min(1) @Max(1440)
        @Column(name = "DAST_POLL_TIMEOUT_MIN", nullable = false)
        Integer dastPollTimeoutMinutes,
        @NotNull @Min(1) @Max(3600)
        @Column(name = "DAST_POLL_INTERVAL_SEC", nullable = false)
        Integer dastPollIntervalSeconds,
        @NotNull @Min(1) @Max(1440)
        @Column(name = "DAST_REPORT_TIMEOUT_MIN", nullable = false)
        Integer dastReportTimeoutMinutes,
        @NotNull @Min(1) @Max(3600)
        @Column(name = "DAST_REPORT_INTERVAL_SEC", nullable = false)
        Integer dastReportIntervalSeconds,
        @NotNull
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SONAR_WAIT_FOR_QUALITY_GATE", nullable = false)
        Boolean sonarWaitForQualityGate,
        @NotNull @Min(1) @Max(1440)
        @Column(name = "SONAR_QUALITY_GATE_TIMEOUT_MIN", nullable = false)
        Integer sonarQualityGateTimeoutMinutes) {

    public void writeTo(ConfigTree defaults) {
        defaults.set("coverage.minLine", coverageMinLine)
                .set("tools.sonar.qualityGate.waitForQualityGate", sonarWaitForQualityGate)
                .set("tools.sonar.qualityGate.timeoutMinutes", sonarQualityGateTimeoutMinutes)
                .set("sast.prepareTimeoutMin", sastPrepareTimeoutMinutes)
                .set("sast.pollTimeoutMin", sastPollTimeoutMinutes)
                .set("sast.pollIntervalSec", sastPollIntervalSeconds)
                .set("sca.enabled", scaEnabled)
                .set("sca.pollTimeoutMin", scaPollTimeoutMinutes)
                .set("sca.pollIntervalSec", scaPollIntervalSeconds)
                .set("dast.pollTimeoutMin", dastPollTimeoutMinutes)
                .set("dast.pollIntervalSec", dastPollIntervalSeconds)
                .set("dast.reportTimeoutMin", dastReportTimeoutMinutes)
                .set("dast.reportIntervalSec", dastReportIntervalSeconds);
    }
}
