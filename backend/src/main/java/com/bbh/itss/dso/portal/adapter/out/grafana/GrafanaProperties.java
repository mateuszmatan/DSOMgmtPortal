package com.bbh.itss.dso.portal.adapter.out.grafana;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dso.grafana")
public record GrafanaProperties(String dashboardUrl, String securityDashboardUrl) {

    public GrafanaProperties {
        dashboardUrl = blankToNull(dashboardUrl);
        securityDashboardUrl = blankToNull(securityDashboardUrl);
    }

    private static String blankToNull(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }
}
