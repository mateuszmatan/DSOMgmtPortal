package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

public record ScanSettings(
        Integer coverageMinLine,
        Integer sastPrepareTimeoutMinutes,
        Integer sastPollTimeoutMinutes,
        Integer sastPollIntervalSeconds,
        Boolean scaEnabled,
        Integer scaPollTimeoutMinutes,
        Integer scaPollIntervalSeconds,
        Integer dastPollTimeoutMinutes,
        Integer dastPollIntervalSeconds,
        Integer dastReportTimeoutMinutes,
        Integer dastReportIntervalSeconds,
        Boolean sonarWaitForQualityGate,
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
