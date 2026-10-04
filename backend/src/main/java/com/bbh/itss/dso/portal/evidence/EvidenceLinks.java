package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.common.Text;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

public record EvidenceLinks(String buildUrl, String appScanUrl, String sonarUrl, String nexusIqUrl) {

    static final String PIPELINE_REPORT = "Pipeline_20Report/";

    static EvidenceLinks of(String jobUrl, Long build, String asocUrl, String appScanApplicationId,
                            String sonarServerUrl, String sonarProjectKey, String nexusIqServerUrl) {
        String buildUrl = jobUrl == null || build == null ? null : withSlash(jobUrl) + build + "/";
        String appScan = Text.isBlank(asocUrl) || Text.isBlank(appScanApplicationId) ? null
                : withoutSlash(asocUrl) + "/main/myapps/" + segment(appScanApplicationId) + "/scans";
        String sonar = Text.isBlank(sonarServerUrl) || Text.isBlank(sonarProjectKey) ? null
                : withoutSlash(sonarServerUrl) + "/dashboard?id="
                        + UriUtils.encodeQueryParam(sonarProjectKey, StandardCharsets.UTF_8);
        String nexusIq = Text.isBlank(nexusIqServerUrl) ? null : withSlash(nexusIqServerUrl);
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

    private static String segment(String value) {
        return UriUtils.encodePathSegment(value.trim(), StandardCharsets.UTF_8);
    }

    private static String withSlash(String url) {
        return withoutSlash(url) + "/";
    }

    private static String withoutSlash(String url) {
        return url.trim().replaceAll("/+$", "");
    }
}
