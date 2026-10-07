package com.bbh.itss.dso.portal.adapter.out.grafana;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@ConfigurationProperties("dso.grafana")
public record GrafanaProperties(String dashboardUrl, String securityDashboardUrl) {

    public GrafanaProperties {
        dashboardUrl = trimToNull(dashboardUrl);
        securityDashboardUrl = trimToNull(securityDashboardUrl);
    }
}
