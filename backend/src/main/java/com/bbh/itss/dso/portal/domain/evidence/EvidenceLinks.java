package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.UriEncoding;

public record EvidenceLinks(String buildUrl, String appScanUrl, String sonarUrl, String nexusIqUrl) {

    static final String PIPELINE_REPORT = "Pipeline_20Report/";

    public static EvidenceLinks of(String buildUrl, String asocUrl, String appScanApplicationId,
                                   String sonarServerUrl, String sonarProjectKey, String nexusIqServerUrl) {
        String appScan = Text.isBlank(asocUrl) || Text.isBlank(appScanApplicationId) ? null
                : Text.withoutTrailingSlash(asocUrl) + "/main/myapps/" + UriEncoding.pathSegment(appScanApplicationId.trim())
                        + "/scans";
        String sonar = Text.isBlank(sonarServerUrl) || Text.isBlank(sonarProjectKey) ? null
                : Text.withoutTrailingSlash(sonarServerUrl) + "/dashboard?id=" + UriEncoding.queryParam(sonarProjectKey);
        String nexusIq = Text.isBlank(nexusIqServerUrl) ? null : Text.withoutTrailingSlash(nexusIqServerUrl) + "/";
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
