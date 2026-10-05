package com.bbh.itss.dso.portal.adapter.out.influx;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class InfluxQueryClient {

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

    public static String literal(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("${", "\\${") + "\"";
    }
}
