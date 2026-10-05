package com.bbh.itss.dso.portal.domain.pipeline;

public enum PipelineType {
    FULL("devSecOpsPipeline", "full", ""),
    SECURITY("devSecOpsSecurityPipeline", "security", "security"),
    EXTENDED("devSecOpsExtendedPipeline", "extended", "extended"),
    SAST("devSecOpsSASTScanningPipeline", "sast", "sast");

    private final String entryPoint;
    private final String variant;
    private final String projectTagSuffix;

    PipelineType(String entryPoint, String variant, String projectTagSuffix) {
        this.entryPoint = entryPoint;
        this.variant = variant;
        this.projectTagSuffix = projectTagSuffix;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public String variant() {
        return variant;
    }

    public String influxProjectTag(String influxProject) {
        return influxProject + projectTagSuffix;
    }
}
