package com.bbh.itss.dso.portal.application.monitoring.port.out;

import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PipelineRunsPort {

    boolean configured();

    void ping();

    Map<MetricsTag, PipelineRun> latestRuns(Collection<MetricsTag> tags);

    List<PipelineRun> recentRuns(MetricsTag tag, int days, int limit);

    List<DoraPoint> doraPoints(MetricsTag tag, int days);
}
