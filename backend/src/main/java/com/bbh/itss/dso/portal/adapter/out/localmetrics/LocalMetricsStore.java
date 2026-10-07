package com.bbh.itss.dso.portal.adapter.out.localmetrics;

import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort;
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort;
import com.bbh.itss.dso.portal.domain.evidence.EvidencePoint;
import com.bbh.itss.dso.portal.domain.evidence.RunEvidence;
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint;
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsRow;
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag;
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Primary
@ConditionalOnExpression(LocalMetricsStore.ACTIVE)
class LocalMetricsStore implements PipelineRunsPort, RunEvidencePort {

    static final String ACTIVE = "${dso.demo-data:false} and '${dso.influx.url:}' == ''";
    private static final TypeReference<Map<String, String>> VALUES = new TypeReference<>() {
    };
    private static final String SELECT =
            "SELECT MEASUREMENT, PROJECT, ENV, JOB, RECORDED_AT, POINT_VALUES FROM DSO_METRIC_POINT p WHERE ";

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    LocalMetricsStore(NamedParameterJdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public void ping() {
        count();
    }

    long count() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM DSO_METRIC_POINT", Long.class);
    }

    void save(List<StoredPoint> points) {
        jdbc.batchUpdate("INSERT INTO DSO_METRIC_POINT (MEASUREMENT, PROJECT, ENV, JOB, RECORDED_AT, POINT_VALUES) "
                + "VALUES (:measurement, :project, :env, :job, :time, :values)", points.stream()
                .map(point -> new MapSqlParameterSource()
                        .addValue("measurement", point.measurement())
                        .addValue("project", point.tag().project())
                        .addValue("env", point.tag().env())
                        .addValue("job", point.job())
                        .addValue("time", at(point.time()))
                        .addValue("values", json.writeValueAsString(point.values())))
                .toArray(SqlParameterSource[]::new));
    }

    @Override
    public LatestRuns latestRuns(Collection<MetricsTag> tags, Set<MetricsTag> sharedTags) {
        Map<MetricsTag, List<PipelineRun>> runs = new HashMap<>();
        if (!tags.isEmpty()) {
            query(SELECT + "MEASUREMENT = 'pipeline_run' AND PROJECT IN (:projects) AND RECORDED_AT = "
                    + "(SELECT MAX(q.RECORDED_AT) FROM DSO_METRIC_POINT q WHERE q.MEASUREMENT = p.MEASUREMENT "
                    + "AND q.PROJECT = p.PROJECT AND q.ENV = p.ENV AND COALESCE(q.JOB, '-') = COALESCE(p.JOB, '-'))",
                    Map.of("projects", projects(tags))).stream()
                    .filter(point -> tags.contains(point.tag()))
                    .forEach(point -> runs.computeIfAbsent(point.tag(), tag -> new ArrayList<>())
                            .add(MetricsRow.run(point.row())));
        }
        return new LatestRuns(runs, sharedTags);
    }

    @Override
    public List<PipelineRun> recentRuns(MetricsTag tag, String job, int days, int limit) {
        return series("pipeline_run", List.of(tag), days).stream()
                .filter(point -> job == null || point.job() != null
                        && (point.job().equals(job) || point.job().startsWith(job + "/")))
                .sorted(Comparator.comparing(StoredPoint::time).reversed())
                .limit(limit)
                .map(point -> MetricsRow.run(point.row()))
                .toList();
    }

    @Override
    public Map<MetricsTag, List<DoraPoint>> doraPoints(Collection<MetricsTag> tags, int days) {
        return series("dora", tags, days).stream().collect(Collectors.groupingBy(StoredPoint::tag,
                Collectors.mapping(point -> MetricsRow.doraPoint(point.row()), Collectors.toList())));
    }

    @Override
    public Map<PipelineRun, RunEvidence> evidenceOf(Map<MetricsTag, Set<PipelineRun>> runs) {
        Map<PipelineRun, RunEvidence> evidence = new HashMap<>();
        runs.forEach((tag, tagged) -> tagged.forEach(run -> evidence.put(run, new RunEvidence(
                query(SELECT + "PROJECT = :project AND ENV = :env AND MEASUREMENT IN (:measurements) "
                                + "AND RECORDED_AT BETWEEN :from AND :to",
                        Map.of("project", tag.project(), "env", tag.env(), "measurements", RunEvidence.MEASUREMENTS,
                                "from", at(RunEvidence.windowStart(run)), "to", at(RunEvidence.windowEnd(run))))
                        .stream()
                        .filter(point -> RunEvidence.recordedDuring(run, point.measurement(), point.time()))
                        .map(point -> new EvidencePoint(point.measurement(), point.row()))
                        .toList()))));
        return evidence;
    }

    private List<StoredPoint> series(String measurement, Collection<MetricsTag> tags, int days) {
        if (tags.isEmpty()) {
            return List.of();
        }
        return query(SELECT + "MEASUREMENT = :measurement AND PROJECT IN (:projects) AND RECORDED_AT >= :since "
                        + "ORDER BY RECORDED_AT",
                Map.of("measurement", measurement, "projects", projects(tags),
                        "since", at(clock.instant().minus(Duration.ofDays(days))))).stream()
                .filter(point -> tags.contains(point.tag()))
                .toList();
    }

    private List<StoredPoint> query(String sql, Map<String, ?> parameters) {
        return jdbc.query(sql, parameters, (row, number) -> new StoredPoint(row.getString("MEASUREMENT"),
                new MetricsTag(row.getString("PROJECT"), row.getString("ENV")), row.getString("JOB"),
                row.getObject("RECORDED_AT", OffsetDateTime.class).toInstant(),
                json.readValue(row.getString("POINT_VALUES"), VALUES)));
    }

    private static List<String> projects(Collection<MetricsTag> tags) {
        return tags.stream().map(MetricsTag::project).distinct().toList();
    }

    private static OffsetDateTime at(Instant time) {
        return time.atOffset(ZoneOffset.UTC);
    }
}
