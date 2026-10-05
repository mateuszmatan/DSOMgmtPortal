package com.bbh.itss.dso.portal.application.monitoring.port.out;

import com.bbh.itss.dso.portal.domain.monitoring.DashboardLinks;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;

import java.util.Optional;

public interface DashboardLinksPort {

    Optional<String> url();

    Optional<DashboardLinks> links(MetricsTag tag, int rangeDays);
}
