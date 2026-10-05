package com.bbh.itss.dso.portal.monitoring;

import com.bbh.itss.dso.portal.adapter.out.influx.InfluxProperties;
import com.bbh.itss.dso.portal.adapter.out.influx.InfluxQueryClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bbh.itss.dso.portal.adapter.out.influx.InfluxQueryClient.literal;

@Component
public class PipelineMetricsRepository {

    private final InfluxQueryClient influx;
    private final InfluxProperties properties;

    public PipelineMetricsRepository(InfluxQueryClient influx, InfluxProperties properties) {
        this.influx = influx;
        this.properties = properties;
    }

    public boolean configured() {
        return influx.configured();
    }

    public void ping() {
        influx.query("buckets() |> limit(n: 1)");
    }

    public Map<MetricsTag, PipelineRun> latestRuns(Collection<MetricsTag> tags) {
        if (tags.isEmpty()) {
            return Map.of();
        }
        String projects = tags.stream().map(MetricsTag::project).distinct().map(InfluxQueryClient::literal)
                .collect(Collectors.joining(", "));
        String flux = """
                from(bucket: %s)
                  |> range(start: -%s)
                  |> filter(fn: (r) => r._measurement == "pipeline_run")
                  |> filter(fn: (r) => contains(value: r.project, set: [%s]))
                  |> last()
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group(columns: ["project", "env"])
                  |> sort(columns: ["_time"])
                  |> last(column: "_time")
                """.formatted(literal(properties.bucket()), properties.lastRunLookback(), projects);
        Map<MetricsTag, PipelineRun> runs = new HashMap<>();
        for (Map<String, String> row : influx.query(flux)) {
            MetricsTag tag = new MetricsTag(row.get("project"), row.get("env"));
            if (tags.contains(tag)) {
                runs.put(tag, PipelineRun.fromRow(row));
            }
        }
        return runs;
    }

    public List<PipelineRun> recentRuns(MetricsTag tag, int days, int limit) {
        String flux = """
                from(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "pipeline_run")
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"], desc: true)
                  |> limit(n: %d)
                """.formatted(literal(properties.bucket()), days, literal(tag.project()), literal(tag.env()), limit);
        return influx.query(flux).stream().map(PipelineRun::fromRow).toList();
    }

    public List<DoraPoint> doraPoints(MetricsTag tag, int days) {
        String flux = """
                from(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "dora")
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> filter(fn: (r) => r._field == "deployment" or r._field == "change_failure" or r._field == "lead_time_s" or r._field == "duration_s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"])
                """.formatted(literal(properties.bucket()), days, literal(tag.project()), literal(tag.env()));
        return influx.query(flux).stream().map(PipelineMetricsRepository::doraPoint).filter(Objects::nonNull).toList();
    }

    static DoraPoint doraPoint(Map<String, String> row) {
        if (row.get("_time") == null) {
            return null;
        }
        return new DoraPoint(Instant.parse(row.get("_time")), positive(row.get("deployment")),
                positive(row.get("change_failure")), orZero(PipelineRun.number(row.get("lead_time_s"))),
                orZero(PipelineRun.number(row.get("duration_s"))));
    }

    private static boolean positive(String value) {
        Long number = PipelineRun.number(value);
        return number != null && number > 0;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
