package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitorPipelinesUseCase monitoring;

    public MonitoringController(MonitorPipelinesUseCase monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/status")
    public MonitoringStatusResponse status() {
        return MonitoringStatusResponse.from(monitoring.status());
    }

    @GetMapping("/products")
    public MonitoringOverviewResponse overview() {
        return MonitoringOverviewResponse.from(monitoring.overview());
    }

    @GetMapping("/products/{id}")
    public ProductMonitoringResponse product(@PathVariable long id) {
        return ProductMonitoringResponse.from(monitoring.product(id));
    }

    @GetMapping("/pipelines/{id}")
    public PipelineMonitoringResponse pipeline(@PathVariable long id,
                                               @RequestParam(defaultValue = "30d") String range) {
        return PipelineMonitoringResponse.from(monitoring.pipeline(id, range));
    }
}
