package com.bbh.itss.dso.portal.adapter.out.influx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("dso.influx")
public record InfluxProperties(
        String url,
        @DefaultValue("DevSecOps") String org,
        @DefaultValue("DORA-metrics") String bucket,
        String token,
        @DefaultValue("365d") String lastRunLookback) {

    public InfluxProperties {
        Flux.duration(lastRunLookback);
    }

    public boolean configured() {
        return url != null && !url.isBlank();
    }
}
