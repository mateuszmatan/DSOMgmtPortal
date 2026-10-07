package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.shared.UriEncoding;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.trim;

public record EvidenceLinks(String buildUrl, String appScanUrl, String sonarUrl, String nexusIqUrl) {

    static final String PIPELINE_REPORT = "Pipeline_20Report/";

    public static EvidenceLinks of(String buildUrl, String asocUrl, String appScanApplicationId,
                                   String sonarServerUrl, String sonarProjectKey, String nexusIqServerUrl) {
        String appScan = isBlank(asocUrl) || isBlank(appScanApplicationId) ? null
                : stripEnd(trim(asocUrl), "/") + "/main/myapps/" + UriEncoding.pathSegment(appScanApplicationId.trim())
                        + "/scans";
        String sonar = isBlank(sonarServerUrl) || isBlank(sonarProjectKey) ? null
                : stripEnd(trim(sonarServerUrl), "/") + "/dashboard?id=" + UriEncoding.queryParam(sonarProjectKey);
        String nexusIq = isBlank(nexusIqServerUrl) ? null : stripEnd(trim(nexusIqServerUrl), "/") + "/";
        return new EvidenceLinks(buildUrl, appScan, sonar, nexusIq);
    }

    public String reportUrl() {
        return buildUrl == null ? null : buildUrl + PIPELINE_REPORT;
    }

    public String testReportUrl() {
        return buildUrl == null ? null : buildUrl + "testReport/";
    }

    public String artifactsUrl() {
        return buildUrl == null ? null : buildUrl + "artifact/";
    }
}
