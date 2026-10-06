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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
class InfluxRunEvidenceAdapter implements RunEvidencePort {

    private static final Duration TOLERANCE = Duration.ofSeconds(2);
    private static final String MEASUREMENT = "_measurement";
    private static final String TABLE = "run";

    private final InfluxQueryClient influx;

    InfluxRunEvidenceAdapter(InfluxQueryClient influx) {
        this.influx = influx;
    }

    @Override
    public Map<PipelineRun, RunEvidence> evidenceOf(Map<MetricsTag, Set<PipelineRun>> runs) {
        List<TaggedRun> ordered = runs.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(run -> new TaggedRun(entry.getKey(), run)))
                .sorted(Comparator.comparing((TaggedRun tagged) -> tagged.tag().project())
                        .thenComparing(tagged -> tagged.tag().env())
                        .thenComparing(tagged -> tagged.run().time()))
                .toList();
        if (ordered.isEmpty()) {
            return Map.of();
        }
        String flux = flux(ordered);
        return influx.read(() -> {
            Map<PipelineRun, List<EvidencePoint>> points = new HashMap<>();
            ordered.forEach(tagged -> points.put(tagged.run(), new ArrayList<>()));
            for (Map<String, String> row : influx.query(flux)) {
                MetricsTag tag = InfluxRows.tag(row);
                for (PipelineRun run : runs.getOrDefault(tag, Set.of())) {
                    if (belongsTo(row, run)) {
                        points.get(run).add(new EvidencePoint(row.get(MEASUREMENT), row));
                    }
                }
            }
            Map<PipelineRun, RunEvidence> evidence = new HashMap<>();
            points.forEach((run, found) -> evidence.put(run, new RunEvidence(found)));
            return evidence;
        });
    }

    private String flux(List<TaggedRun> ordered) {
        String tables = IntStream.range(0, ordered.size())
                .mapToObj(index -> table(index, ordered.get(index).tag(), ordered.get(index).run()))
                .collect(Collectors.joining());
        String pivot = "  |> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")\n";
        if (ordered.size() == 1) {
            return tables + TABLE + "0\n" + pivot;
        }
        return tables + "union(tables: [" + IntStream.range(0, ordered.size()).mapToObj(index -> TABLE + index)
                .collect(Collectors.joining(", ")) + "])\n" + pivot;
    }

    private String table(int index, MetricsTag tag, PipelineRun run) {
        return """
                %s%d = from(bucket: %s)
                  |> range(start: time(v: %s), stop: time(v: %s))
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> filter(fn: (r) => %s)
                  |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
                  |> last()
                """.formatted(TABLE, index, influx.bucket(), Flux.string(startOf(run).toString()),
                Flux.string(run.time().plus(TOLERANCE).toString()), Flux.string(tag.project()),
                Flux.string(tag.env()), measurements());
    }

    private static String measurements() {
        return RunEvidence.MEASUREMENTS.stream().map(measurement -> "r._measurement == " + Flux.string(measurement))
                .collect(Collectors.joining(" or "));
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

    private record TaggedRun(MetricsTag tag, PipelineRun run) {
    }
}
