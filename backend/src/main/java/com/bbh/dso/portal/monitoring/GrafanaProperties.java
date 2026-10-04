package com.bbh.dso.portal.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Grafana dashboard whose panels are embedded in the pipeline details page. The dashboard must define the
 * {@code project}, {@code env} and {@code variant} variables, and Grafana must allow embedding
 * ({@code security.allow_embedding = true}).
 */
@ConfigurationProperties("dso.grafana")
public record GrafanaProperties(
        String url,
        @DefaultValue("1") int orgId,
        @DefaultValue("dso-portal-dora") String dashboardUid,
        @DefaultValue("devsecops-pipeline-dora") String dashboardSlug,
        @DefaultValue("light") String theme,
        List<Panel> panels) {

    public GrafanaProperties {
        panels = panels == null || panels.isEmpty() ? DEFAULT_PANELS : List.copyOf(panels);
    }

    /** Panels of grafana/dso-portal-dora.json, the dashboard shipped with the portal. */
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

    /**
     * @param width share of the row out of 12 columns
     */
    public record Panel(int id, String title, @DefaultValue("6") int width) {
    }
}
