package com.bbh.dso.portal.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The InfluxDB 2 instance the DevSecOps pipelines write their metrics to. The token only needs read access
 * to the bucket.
 */
@ConfigurationProperties("dso.influx")
public record InfluxProperties(
        String url,
        @DefaultValue("DevSecOps") String org,
        @DefaultValue("DORA-metrics") String bucket,
        String token,
        @DefaultValue("365d") String lastRunLookback) {

    public boolean configured() {
        return url != null && !url.isBlank();
    }
}
