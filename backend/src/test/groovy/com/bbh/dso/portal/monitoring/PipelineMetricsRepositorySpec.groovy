package com.bbh.dso.portal.monitoring

import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class PipelineMetricsRepositorySpec extends Specification {

    InfluxQueryClient influx = Mock()
    InfluxProperties properties = new InfluxProperties('http://influx', 'DevSecOps', 'DORA-metrics', 't', '180d')

    @Subject
    def repository = new PipelineMetricsRepository(influx, properties)

    def gui = new MetricsTag('CERT-gui', 'test')
    def guiSast = new MetricsTag('CERT-guisast', 'test')

    def "it is configured when the client is"() {
        given:
        influx.configured() >> true

        expect:
        repository.configured()
    }

    def "a ping queries the buckets"() {
        when:
        repository.ping()

        then:
        1 * influx.query('buckets() |> limit(n: 1)') >> []
    }

    def "no pipelines need no query"() {
        when:
        def runs = repository.latestRuns([])

        then:
        runs == [:]
        0 * influx.query(_)
    }

    def "the latest run of each pipeline is read in one query"() {
        when:
        def runs = repository.latestRuns([gui, guiSast, new MetricsTag('CERT-gui', 'uat')] as LinkedHashSet)

        then:
        1 * influx.query({ String flux ->
            flux.contains('from(bucket: "DORA-metrics")') && flux.contains('range(start: -180d)') &&
                    flux.contains('set: ["CERT-gui", "CERT-guisast"]') && flux.contains('last(column: "_time")')
        }) >> [run('2026-10-01T10:00:00Z', 'CERT-gui', 'test', 'SUCCESS'),
               run('2026-10-02T10:00:00Z', 'CERT-guisast', 'test', 'FAILURE'),
               run('2026-10-03T10:00:00Z', 'CERT-gui', 'prod', 'SUCCESS')]
        runs.keySet() == [gui, guiSast] as Set
        runs[guiSast].result == RunResult.FAILURE
        runs[gui].time == Instant.parse('2026-10-01T10:00:00Z')
    }

    def "recent runs are read newest first for one pipeline"() {
        when:
        def runs = repository.recentRuns(gui, 30, 25)

        then:
        1 * influx.query({ String flux ->
            flux.contains('range(start: -30d)') && flux.contains('r.project == "CERT-gui" and r.env == "test"') &&
                    flux.contains('sort(columns: ["_time"], desc: true)') && flux.contains('limit(n: 25)')
        }) >> [run('2026-10-02T10:00:00Z', 'CERT-gui', 'test', 'UNSTABLE'), run('2026-10-01T10:00:00Z', 'CERT-gui', 'test', 'SUCCESS')]
        runs*.result == [RunResult.UNSTABLE, RunResult.SUCCESS]
    }

    def "DORA points are read from the dora measurement"() {
        when:
        def points = repository.doraPoints(gui, 90)

        then:
        1 * influx.query({ String flux -> flux.contains('r._measurement == "dora"') && flux.contains('range(start: -90d)') }) >> [
                [_time: '2026-10-01T10:00:00Z', deployment: '1', change_failure: '0', lead_time_s: '3600', duration_s: '600.0'],
                [_time: '2026-10-02T10:00:00Z', change_failure: '1'],
                [deployment: '1']]
        points == [new DoraPoint(Instant.parse('2026-10-01T10:00:00Z'), true, false, 3600, 600),
                   new DoraPoint(Instant.parse('2026-10-02T10:00:00Z'), false, true, 0, 0)]
    }

    private static Map<String, String> run(String time, String project, String env, String result) {
        [_time: time, project: project, env: env, result: result, build: '7', duration_s: '1200']
    }
}
