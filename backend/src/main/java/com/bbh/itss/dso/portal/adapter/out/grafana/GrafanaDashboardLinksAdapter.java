package com.bbh.itss.dso.portal.adapter.out.grafana;

import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort;
import com.bbh.itss.dso.portal.domain.monitoring.DashboardLinks;
import com.bbh.itss.dso.portal.domain.monitoring.DashboardPanel;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class GrafanaDashboardLinksAdapter implements DashboardLinksPort {

    private final GrafanaProperties grafana;

    GrafanaDashboardLinksAdapter(GrafanaProperties grafana) {
        this.grafana = grafana;
    }

    @Override
    public Optional<String> url() {
        return grafana.configured() ? Optional.of(grafana.url()) : Optional.empty();
    }

    @Override
    public Optional<DashboardLinks> links(MetricsTag tag, int rangeDays) {
        if (!grafana.configured()) {
            return Optional.empty();
        }
        String base = grafana.url().replaceAll("/+$", "");
        String path = "/" + grafana.dashboardUid() + "/" + grafana.dashboardSlug();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("orgId", String.valueOf(grafana.orgId()));
        query.put("var-project", tag.project());
        query.put("var-env", tag.env());
        query.put("from", "now-" + rangeDays + "d");
        query.put("to", "now");
        query.put("theme", grafana.theme());

        List<DashboardPanel> panels = grafana.panels().stream()
                .map(panel -> new DashboardPanel(panel.id(), panel.title(), panel.width(),
                        url(base + "/d-solo" + path, query, panel.id())))
                .toList();
        return Optional.of(new DashboardLinks(url(base + "/d" + path, query, null), panels));
    }

    private static String url(String path, Map<String, String> query, Integer panelId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        query.forEach((name, value) ->
                builder.queryParam(name, UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8)));
        if (panelId != null) {
            builder.queryParam("panelId", panelId);
        }
        return builder.build(true).toUriString();
    }
}
