package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.ScanSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record ScanSettingsEmbeddable(
        @Column(name = "COVERAGE_MIN_LINE", nullable = false) Integer coverageMinLine,
        @Column(name = "SAST_PREPARE_TIMEOUT_MIN", nullable = false) Integer sastPrepareTimeoutMinutes,
        @Column(name = "SAST_POLL_TIMEOUT_MIN", nullable = false) Integer sastPollTimeoutMinutes,
        @Column(name = "SAST_POLL_INTERVAL_SEC", nullable = false) Integer sastPollIntervalSeconds,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SCA_ENABLED", nullable = false) Boolean scaEnabled,
        @Column(name = "SCA_POLL_TIMEOUT_MIN", nullable = false) Integer scaPollTimeoutMinutes,
        @Column(name = "SCA_POLL_INTERVAL_SEC", nullable = false) Integer scaPollIntervalSeconds,
        @Column(name = "DAST_POLL_TIMEOUT_MIN", nullable = false) Integer dastPollTimeoutMinutes,
        @Column(name = "DAST_POLL_INTERVAL_SEC", nullable = false) Integer dastPollIntervalSeconds,
        @Column(name = "DAST_REPORT_TIMEOUT_MIN", nullable = false) Integer dastReportTimeoutMinutes,
        @Column(name = "DAST_REPORT_INTERVAL_SEC", nullable = false) Integer dastReportIntervalSeconds,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SONAR_WAIT_FOR_QUALITY_GATE", nullable = false) Boolean sonarWaitForQualityGate,
        @Column(name = "SONAR_QUALITY_GATE_TIMEOUT_MIN", nullable = false) Integer sonarQualityGateTimeoutMinutes) {

    static ScanSettingsEmbeddable of(ScanSettings scans) {
        return new ScanSettingsEmbeddable(scans.coverageMinLine(), scans.sastPrepareTimeoutMinutes(),
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
