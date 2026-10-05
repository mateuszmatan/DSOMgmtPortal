package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.monitoring.DashboardLinks;

import java.util.List;

public record GrafanaLinksResponse(String dashboardUrl, List<Panel> panels) {

    static GrafanaLinksResponse from(DashboardLinks links) {
        return DtoMapping.mapped(links, found -> new GrafanaLinksResponse(found.dashboardUrl(), found.panels().stream()
                .map(panel -> new Panel(panel.id(), panel.title(), panel.width(), panel.url())).toList()));
    }

    public record Panel(int id, String title, int width, String url) {
    }
}
