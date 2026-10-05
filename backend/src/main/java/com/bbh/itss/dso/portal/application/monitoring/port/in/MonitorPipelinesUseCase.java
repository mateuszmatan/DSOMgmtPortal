package com.bbh.itss.dso.portal.application.monitoring.port.in;

public interface MonitorPipelinesUseCase {

    MonitoringStatus status();

    MonitoringOverview overview();

    ProductMonitoring product(long productId);

    PipelineMonitoring pipeline(long pipelineId, String range);
}
