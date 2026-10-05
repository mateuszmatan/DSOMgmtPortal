package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.adapter.out.grafana.GrafanaProperties;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.GrafanaLinks;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.GrafanaPanel;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GrafanaPanels {

    private final GrafanaProperties grafana;

    public GrafanaPanels(GrafanaProperties grafana) {
        this.grafana = grafana;
    }

    public boolean configured() {
        return grafana.configured();
    }

    public String url() {
        return grafana.url();
    }

    public GrafanaLinks links(MetricsTag tag, int rangeDays) {
        if (!grafana.configured()) {
            return null;
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

        List<GrafanaPanel> panels = grafana.panels().stream()
                .map(panel -> new GrafanaPanel(panel.id(), panel.title(), panel.width(),
                        url(base + "/d-solo" + path, query, panel.id())))
                .toList();
        return new GrafanaLinks(url(base + "/d" + path, query, null), panels);
    }

    private static String url(String path, Map<String, String> query, Integer panelId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        query.forEach((name, value) -> builder.queryParam(name, UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8)));
        if (panelId != null) {
            builder.queryParam("panelId", panelId);
        }
        return builder.build(true).toUriString();
    }
}
