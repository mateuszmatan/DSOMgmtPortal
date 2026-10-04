package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.common.Text;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * The pages a change request links to for one run. They are built from the portal's own settings, since the
 * DevSecOps library records no links: the Jenkins build from the pipeline's job and the build number, the
 * pages the library publishes in every build, the service's AppScan application, its SonarQube project and the
 * Nexus IQ server.
 */
public record EvidenceLinks(String buildUrl, String appScanUrl, String sonarUrl, String nexusIqUrl) {

    /** The HTML report the library publishes in every build as "Pipeline Report". */
    static final String PIPELINE_REPORT = "Pipeline_20Report/";

    /**
     * @param jobUrl the pipeline's Jenkins job, null when unknown
     * @param build  the build number of the run, null when no run was recorded
     */
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
