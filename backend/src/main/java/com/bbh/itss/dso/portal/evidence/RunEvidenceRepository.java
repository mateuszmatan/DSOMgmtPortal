package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.monitoring.InfluxProperties;
import com.bbh.itss.dso.portal.monitoring.InfluxQueryClient;
import com.bbh.itss.dso.portal.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.monitoring.PipelineRun;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bbh.itss.dso.portal.monitoring.InfluxQueryClient.literal;

@Component
public class RunEvidenceRepository {

    private static final Duration TOLERANCE = Duration.ofSeconds(2);

    private final InfluxQueryClient influx;
    private final InfluxProperties properties;

    public RunEvidenceRepository(InfluxQueryClient influx, InfluxProperties properties) {
        this.influx = influx;
        this.properties = properties;
    }

    public Map<MetricsTag, RunPoints> pointsOf(Map<MetricsTag, PipelineRun> runs) {
        if (runs.isEmpty()) {
            return Map.of();
        }
        Instant start = runs.values().stream().map(RunEvidenceRepository::startOf).min(Instant::compareTo).orElseThrow();
        Instant stop = runs.values().stream().map(PipelineRun::time).max(Instant::compareTo).orElseThrow()
                .plus(TOLERANCE);
        String projects = runs.keySet().stream().map(MetricsTag::project).distinct().map(InfluxQueryClient::literal)
                .collect(Collectors.joining(", "));
        String measurements = RunPoints.MEASUREMENTS.stream().map(InfluxQueryClient::literal)
                .collect(Collectors.joining(", "));
        String flux = """
                from(bucket: %s)
                  |> range(start: time(v: %s), stop: time(v: %s))
                  |> filter(fn: (r) => contains(value: r._measurement, set: [%s]))
                  |> filter(fn: (r) => contains(value: r.project, set: [%s]))
                  |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
                  |> last()
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                """.formatted(literal(properties.bucket()), literal(start.toString()), literal(stop.toString()),
                measurements, projects);
        Map<MetricsTag, List<Map<String, String>>> rows = new HashMap<>();
        for (Map<String, String> row : influx.query(flux)) {
            MetricsTag tag = new MetricsTag(row.get("project"), row.get("env"));
            PipelineRun run = runs.get(tag);
            if (run != null && belongsTo(row, run)) {
                rows.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(row);
            }
        }
        Map<MetricsTag, RunPoints> points = new HashMap<>();
        runs.keySet().forEach(tag -> points.put(tag, new RunPoints(rows.getOrDefault(tag, List.of()))));
        return points;
    }

    static boolean belongsTo(Map<String, String> row, PipelineRun run) {
        String time = row.get("_time");
        if (time == null) {
            return false;
        }
        Instant at = Instant.parse(time);
        Instant latest = run.time().plus(TOLERANCE);
        Instant earliest = "stage_event".equals(row.get("_measurement")) ? startOf(run) : run.time().minus(TOLERANCE);
        return !at.isBefore(earliest) && !at.isAfter(latest);
    }

    static Instant startOf(PipelineRun run) {
        long seconds = run.durationSeconds() == null ? 0 : run.durationSeconds();
        return run.time().minusSeconds(seconds).minus(TOLERANCE).minus(TOLERANCE);
    }
}
