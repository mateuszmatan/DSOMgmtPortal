package com.bbh.itss.dso.portal.application.pipeline.port.in;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;

public record PipelineCommand(PipelineType type, PipelineSettings settings) {
}
