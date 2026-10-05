package com.bbh.itss.dso.portal.domain.monitoring;

import java.util.List;

public record DashboardLinks(String dashboardUrl, List<DashboardPanel> panels) {

    public DashboardLinks {
        panels = List.copyOf(panels);
    }
}
