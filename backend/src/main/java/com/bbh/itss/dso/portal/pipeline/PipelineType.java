package com.bbh.itss.dso.portal.pipeline;

/**
 * The four pipelines of the DevSecOps library. {@code variant} is the value the library writes to the
 * InfluxDB {@code variant} tag, and {@code projectTagSuffix} is what it appends to {@code influx.project}
 * to build the {@code project} tag ({@code InfluxDbService.buildContext}).
 */
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
