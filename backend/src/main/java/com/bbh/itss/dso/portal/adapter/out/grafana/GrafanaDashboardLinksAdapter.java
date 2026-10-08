package com.bbh.itss.dso.portal.adapter.out.grafana;

import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.springframework.web.util.UriUtils.encodeQueryParam;

@Component
@RequiredArgsConstructor
class GrafanaDashboardLinksAdapter implements DashboardLinksPort {

    private static final Set<PipelineType> SECURITY_DASHBOARD = Set.of(SECURITY, SAST, NEXUS_IQ);

    private final GrafanaProperties grafana;

    @Override
    public Optional<String> url() {
        return Optional.ofNullable(grafana.dashboardUrl());
    }

    @Override
    public Optional<String> dashboardUrl(MetricsTag tag, PipelineType type, int rangeDays) {
        String dashboard = SECURITY_DASHBOARD.contains(type)
                ? getIfNull(grafana.securityDashboardUrl(), grafana.dashboardUrl()) : grafana.dashboardUrl();
        return Optional.ofNullable(dashboard).map(link -> link + (link.contains("?") ? "&" : "?") + "var-project="
                + encodeQueryParam(tag.project(), UTF_8) + "&from=now-" + rangeDays + "d&to=now");
    }
}
