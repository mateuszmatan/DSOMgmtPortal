package com.bbh.dso.portal.monitoring;

import com.bbh.dso.portal.monitoring.MonitoringDtos.MonitoringStatus;
import com.bbh.dso.portal.monitoring.MonitoringDtos.Overview;
import com.bbh.dso.portal.monitoring.MonitoringDtos.PipelineMonitoring;
import com.bbh.dso.portal.monitoring.MonitoringDtos.ProductMonitoring;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoring;

    public MonitoringController(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/status")
    public MonitoringStatus status() {
        return monitoring.status();
    }

    @GetMapping("/products")
    public Overview overview() {
        return monitoring.overview();
    }

    @GetMapping("/products/{id}")
    public ProductMonitoring product(@PathVariable Long id) {
        return monitoring.product(id);
    }

    @GetMapping("/pipelines/{id}")
    public PipelineMonitoring pipeline(@PathVariable Long id, @RequestParam(defaultValue = "30d") String range) {
        return monitoring.pipeline(id, range);
    }
}
