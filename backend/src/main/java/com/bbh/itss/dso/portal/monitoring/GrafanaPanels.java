package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.GrafanaLinks;
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.GrafanaPanel;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the URLs of the Grafana dashboard and of its single panels ({@code /d-solo}) for one pipeline,
 * passing the pipeline's tags as dashboard variables.
 */
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

    /** The links for a pipeline, or null when Grafana is not configured. */
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

    /** Every value is encoded as a query parameter, so characters such as '&' cannot split it. */
    private static String url(String path, Map<String, String> query, Integer panelId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        query.forEach((name, value) -> builder.queryParam(name, UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8)));
        if (panelId != null) {
            builder.queryParam("panelId", panelId);
        }
        return builder.build(true).toUriString();
    }
}
