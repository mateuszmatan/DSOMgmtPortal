package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.application.monitoring.port.out.MonitoringStatusPort;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
class InfluxPipelineRunsAdapter implements PipelineRunsPort, MonitoringStatusPort {

    private final InfluxQueryClient influx;

    InfluxPipelineRunsAdapter(InfluxQueryClient influx) {
        this.influx = influx;
    }

    @Override
    public boolean configured() {
        return influx.configured();
    }

    @Override
    public void ping() {
        influx.read(() -> influx.query("buckets() |> limit(n: 1)"));
    }

    @Override
    public Map<MetricsTag, PipelineRun> latestRuns(Collection<MetricsTag> tags) {
        if (tags.isEmpty()) {
            influx.requireConfigured();
            return Map.of();
        }
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
                """.formatted(influx.bucket(), influx.lastRunLookback(),
                Flux.strings(tags.stream().map(MetricsTag::project).toList()));
        return influx.read(() -> {
            Map<MetricsTag, PipelineRun> runs = new HashMap<>();
            for (Map<String, String> row : influx.query(flux)) {
                MetricsTag tag = InfluxRows.tag(row);
                if (tags.contains(tag)) {
                    runs.put(tag, InfluxRows.run(row));
                }
            }
            return runs;
        });
    }

    @Override
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
                """.formatted(influx.bucket(), Flux.positive(days), Flux.string(tag.project()),
                Flux.string(tag.env()), Flux.positive(limit));
        return influx.read(() -> influx.query(flux).stream().map(InfluxRows::run).toList());
    }

    @Override
    public List<DoraPoint> doraPoints(MetricsTag tag, int days) {
        String flux = """
                from(bucket: %s)
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "dora")
                  |> filter(fn: (r) => r.project == %s and r.env == %s)
                  |> filter(fn: (r) => r._field == "deployment" or r._field == "change_failure" \
                or r._field == "lead_time_s" or r._field == "duration_s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"])
                """.formatted(influx.bucket(), Flux.positive(days), Flux.string(tag.project()),
                Flux.string(tag.env()));
        return influx.read(() -> influx.query(flux).stream().map(InfluxRows::doraPoint).filter(Objects::nonNull)
                .toList());
    }
}
