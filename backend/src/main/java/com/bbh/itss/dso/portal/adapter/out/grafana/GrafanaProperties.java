package com.bbh.itss.dso.portal.adapter.out.grafana;

import com.bbh.itss.dso.portal.domain.shared.Text;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dso.grafana")
public record GrafanaProperties(String dashboardUrl, String securityDashboardUrl) {

    public GrafanaProperties {
        dashboardUrl = Text.trimToNull(dashboardUrl);
        securityDashboardUrl = Text.trimToNull(securityDashboardUrl);
    }
}
