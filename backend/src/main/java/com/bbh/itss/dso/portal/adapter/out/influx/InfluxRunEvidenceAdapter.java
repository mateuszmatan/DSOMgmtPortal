package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort;
import com.bbh.itss.dso.portal.domain.evidence.EvidencePoint;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class InfluxRunEvidenceAdapter implements RunEvidencePort {

    private static final Duration TOLERANCE = Duration.ofSeconds(2);
    private static final String MEASUREMENT = "_measurement";

    private final InfluxQueryClient influx;

    InfluxRunEvidenceAdapter(InfluxQueryClient influx) {
        this.influx = influx;
    }

    @Override
    public Map<MetricsTag, RunEvidence> evidenceOf(Map<MetricsTag, PipelineRun> runs) {
        if (runs.isEmpty()) {
            return Map.of();
        }
        String flux = flux(runs);
        return influx.read(() -> {
            Map<MetricsTag, List<EvidencePoint>> points = new HashMap<>();
            for (Map<String, String> row : influx.query(flux)) {
                MetricsTag tag = InfluxRows.tag(row);
                PipelineRun run = runs.get(tag);
                if (run != null && belongsTo(row, run)) {
                    points.computeIfAbsent(tag, ignored -> new ArrayList<>())
                            .add(new EvidencePoint(row.get(MEASUREMENT), row));
                }
            }
            Map<MetricsTag, RunEvidence> evidence = new HashMap<>();
            runs.keySet().forEach(tag -> evidence.put(tag, new RunEvidence(points.getOrDefault(tag, List.of()))));
            return evidence;
        });
    }

    private String flux(Map<MetricsTag, PipelineRun> runs) {
        Instant start = runs.values().stream().map(InfluxRunEvidenceAdapter::startOf).min(Instant::compareTo)
                .orElseThrow();
        Instant stop = runs.values().stream().map(PipelineRun::time).max(Instant::compareTo).orElseThrow()
                .plus(TOLERANCE);
        return """
                from(bucket: %s)
                  |> range(start: time(v: %s), stop: time(v: %s))
                  |> filter(fn: (r) => contains(value: r._measurement, set: [%s]))
                  |> filter(fn: (r) => contains(value: r.project, set: [%s]))
                  |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
                  |> last()
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                """.formatted(influx.bucket(), Flux.string(start.toString()), Flux.string(stop.toString()),
                Flux.strings(RunEvidence.MEASUREMENTS),
                Flux.strings(runs.keySet().stream().map(MetricsTag::project).toList()));
    }

    static boolean belongsTo(Map<String, String> row, PipelineRun run) {
        String time = row.get("_time");
        if (time == null || row.get(MEASUREMENT) == null) {
            return false;
        }
        Instant at = Instant.parse(time);
        Instant latest = run.time().plus(TOLERANCE);
        Instant earliest = "stage_event".equals(row.get(MEASUREMENT)) ? startOf(run) : run.time().minus(TOLERANCE);
        return !at.isBefore(earliest) && !at.isAfter(latest);
    }

    static Instant startOf(PipelineRun run) {
        long seconds = run.durationSeconds() == null ? 0 : run.durationSeconds();
        return run.time().minusSeconds(seconds).minus(TOLERANCE).minus(TOLERANCE);
    }
}
