package com.bbh.itss.dso.portal.adapter.out.grafana;

import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
class GrafanaDashboardLinksAdapter implements DashboardLinksPort {

    private final GrafanaProperties grafana;

    GrafanaDashboardLinksAdapter(GrafanaProperties grafana) {
        this.grafana = grafana;
    }

    @Override
    public Optional<String> url() {
        return Optional.ofNullable(grafana.dashboardUrl());
    }

    @Override
    public Optional<String> dashboardUrl(MetricsTag tag, PipelineType type, int rangeDays) {
        boolean security = type == PipelineType.SECURITY || type == PipelineType.SAST;
        String dashboard = security && grafana.securityDashboardUrl() != null ? grafana.securityDashboardUrl()
                : grafana.dashboardUrl();
        return Optional.ofNullable(dashboard).map(link -> link + (link.contains("?") ? "&" : "?") + "var-project="
                + UriUtils.encodeQueryParam(tag.project(), StandardCharsets.UTF_8) + "&from=now-" + rangeDays
                + "d&to=now");
    }
}
