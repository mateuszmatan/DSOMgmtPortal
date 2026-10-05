package com.bbh.itss.dso.portal.application.monitoring.port.out;

import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

import java.util.Optional;

public interface DashboardLinksPort {

    Optional<String> url();

    Optional<String> dashboardUrl(MetricsTag tag, PipelineType type, int rangeDays);
}
