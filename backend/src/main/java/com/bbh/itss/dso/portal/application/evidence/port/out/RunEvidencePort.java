package com.bbh.itss.dso.portal.application.evidence.port.out;

import com.bbh.itss.dso.portal.domain.evidence.RunEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;

import java.util.Map;

public interface RunEvidencePort {

    Map<MetricsTag, RunEvidence> evidenceOf(Map<MetricsTag, PipelineRun> runs);
}
