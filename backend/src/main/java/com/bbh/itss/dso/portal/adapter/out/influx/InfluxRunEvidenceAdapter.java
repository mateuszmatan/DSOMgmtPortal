package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort;
import com.bbh.itss.dso.portal.domain.evidence.EvidencePoint;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.string;
import static com.bbh.itss.dso.portal.domain.evidence.RunEvidence.MEASUREMENTS;
import static com.bbh.itss.dso.portal.domain.evidence.RunEvidence.recordedDuring;
import static com.bbh.itss.dso.portal.domain.evidence.RunEvidence.windowEnd;
import static com.bbh.itss.dso.portal.domain.evidence.RunEvidence.windowStart;
import static com.bbh.itss.dso.portal.domain.monitoring.MetricsRow.tag;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;

@Component
@RequiredArgsConstructor
class InfluxRunEvidenceAdapter implements RunEvidencePort {

    private static final String MEASUREMENT = "_measurement";
    private static final String TABLE = "run";

    private final InfluxQueryClient influx;

    @Override
    public Map<PipelineRun, RunEvidence> evidenceOf(Map<MetricsTag, Set<PipelineRun>> runs) {
        List<TaggedRun> ordered = runs.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(run -> new TaggedRun(entry.getKey(), run)))
                .sorted(comparing((TaggedRun tagged) -> tagged.tag().project())
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
                MetricsTag tag = tag(row);
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
        String tables = range(0, ordered.size())
                .mapToObj(index -> table(index, ordered.get(index).tag(), ordered.get(index).run()))
                .collect(joining());
        String pivot = "  |> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")\n";
        if (ordered.size() == 1) {
            return tables + TABLE + "0\n" + pivot;
        }
        return tables + "union(tables: [" + range(0, ordered.size()).mapToObj(index -> TABLE + index)
                .collect(joining(", ")) + "])\n" + pivot;
    }

    private String table(int index, MetricsTag tag, PipelineRun run) {
        return """
                %s%d = from(bucket: %s)
                  |> range(start: time(v: %s), stop: time(v: %s))
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> filter(fn: (r) => %s)
                  |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
                  |> last()
                """.formatted(TABLE, index, influx.bucket(), string(windowStart(run).toString()),
                string(windowEnd(run).toString()), string(tag.project()), string(tag.env()), measurements());
    }

    private static String measurements() {
        return MEASUREMENTS.stream().map(measurement -> "r._measurement == " + string(measurement))
                .collect(joining(" or "));
    }

    static boolean belongsTo(Map<String, String> row, PipelineRun run) {
        String time = row.get("_time");
        return allNotNull(time, row.get(MEASUREMENT))
                && recordedDuring(run, row.get(MEASUREMENT), Instant.parse(time));
    }

    private record TaggedRun(MetricsTag tag, PipelineRun run) {
    }
}
