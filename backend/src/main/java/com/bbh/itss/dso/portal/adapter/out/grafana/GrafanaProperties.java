package com.bbh.itss.dso.portal.adapter.out.grafana;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("dso.grafana")
public record GrafanaProperties(
        String url,
        @DefaultValue("1") int orgId,
        @DefaultValue("dso-portal-dora") String dashboardUid,
        @DefaultValue("devsecops-pipeline-dora") String dashboardSlug,
        @DefaultValue("light") String theme,
        String datasourceUid,
        List<Panel> panels) {

    public GrafanaProperties {
        datasourceUid = datasourceUid == null || datasourceUid.isBlank() ? null : datasourceUid.trim();
        panels = panels == null || panels.isEmpty() ? DEFAULT_PANELS : List.copyOf(panels);
    }

    static final List<Panel> DEFAULT_PANELS = List.of(
            new Panel(1, "Deployment frequency", 6),
            new Panel(2, "Lead time for changes", 6),
            new Panel(3, "Change failure rate", 4),
            new Panel(4, "Time to restore service", 8),
            new Panel(5, "Run outcome", 12),
            new Panel(6, "Build duration", 6),
            new Panel(7, "Slowest stages", 6),
            new Panel(8, "Findings above policy", 12));

    public boolean configured() {
        return url != null && !url.isBlank();
    }

    public record Panel(int id, String title, @DefaultValue("6") int width) {
    }
}
