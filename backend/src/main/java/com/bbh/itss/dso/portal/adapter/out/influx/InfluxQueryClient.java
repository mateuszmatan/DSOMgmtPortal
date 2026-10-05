package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.domain.monitoring.MetricsUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class InfluxQueryClient {

    static final String NOT_CONFIGURED = "InfluxDB is not configured for the portal";
    static final String UNREADABLE = "InfluxDB could not be read: ";
    private static final int MAX_REASON = 300;
    private static final Logger log = LoggerFactory.getLogger(InfluxQueryClient.class);

    private final InfluxProperties properties;
    private final RestClient client;

    public InfluxQueryClient(InfluxProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.client = properties.configured()
                ? builder.baseUrl(properties.url()).requestFactory(requestFactory).build()
                : null;
    }

    public boolean configured() {
        return client != null;
    }

    String bucket() {
        return Flux.string(properties.bucket());
    }

    String lastRunLookback() {
        return Flux.duration(properties.lastRunLookback());
    }

    void requireConfigured() {
        if (!configured()) {
            throw new MetricsUnavailableException(NOT_CONFIGURED);
        }
    }

    <T> T read(Supplier<T> reading) {
        requireConfigured();
        try {
            return reading.get();
        } catch (MetricsUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Reading from InfluxDB failed: {}", e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new MetricsUnavailableException(UNREADABLE
                    + (message.length() > MAX_REASON ? message.substring(0, MAX_REASON) : message));
        }
    }

    public List<Map<String, String>> query(String flux) {
        if (client == null) {
            throw new IllegalStateException("InfluxDB is not configured");
        }
        String csv = client.post()
                .uri(uri -> uri.path("/api/v2/query").queryParam("org", properties.org()).build())
                .header("Authorization", "Token " + (properties.token() == null ? "" : properties.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType("application/csv"))
                .body(Map.of("query", flux, "type", "flux",
                        "dialect", Map.of("header", true, "annotations", List.of(), "delimiter", ",")))
                .retrieve()
                .body(String.class);
        return FluxCsv.parse(csv);
    }
}
