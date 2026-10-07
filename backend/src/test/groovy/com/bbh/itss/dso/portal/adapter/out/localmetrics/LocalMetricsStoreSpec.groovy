package com.bbh.itss.dso.portal.adapter.out.localmetrics

import com.bbh.itss.dso.portal.domain.evidence.ReleaseGateEvidence
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:local-metrics;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LocalMetricsStoreSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-07T12:00:00Z')

    @Autowired
    JdbcTemplate jdbc

    LocalMetricsStore store

    def gui = new MetricsTag('CERT-gui', 'test')
    def guiUat = new MetricsTag('CERT-gui', 'uat')
    def api = new MetricsTag('CERT-backend-api', 'test')
    def shared = new MetricsTag('CertScanner', 'test')

    def setup() {
        store = new LocalMetricsStore(new NamedParameterJdbcTemplate(jdbc), JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC))
    }

    def "points are saved and counted, and a ping reads the store"() {
        when:
        store.save([run(gui, hours(3), 'SUCCESS'), run(api, hours(2), 'FAILURE'), dora(gui, hours(3), true)])
        store.ping()

        then:
        store.configured()
        store.count() == 3
    }

    def "the latest run of each tag is read, and of each Jenkins job for a tag services share"() {
        given:
        store.save([run(gui, hours(30), 'FAILURE'), run(gui, hours(3), 'SUCCESS'), run(guiUat, hours(1), 'UNSTABLE'),
                    run(api, hours(2), 'FAILURE'), dora(gui, hours(1), false),
                    run(shared, hours(5), 'SUCCESS', 'DevSecOps/CERT/gui-full'),
                    run(shared, hours(4), 'FAILURE', 'DevSecOps/CERT/api-full'),
                    run(shared, hours(9), 'SUCCESS', 'DevSecOps/CERT/api-full')])

        when:
        def latest = store.latestRuns([gui, api, shared], [shared] as Set)

        then:
        latest.runs().keySet() == [gui, api, shared] as Set
        latest.runs()[gui]*.result() == [RunResult.SUCCESS]
        latest.runs()[api]*.result() == [RunResult.FAILURE]
        latest.runs()[shared].collectEntries { [it.job(), it.result()] } ==
                ['DevSecOps/CERT/gui-full': RunResult.SUCCESS, 'DevSecOps/CERT/api-full': RunResult.FAILURE]
        latest.sharedTags() == [shared] as Set
        store.latestRuns([], [] as Set) == LatestRuns.none()
    }

    def "recent runs are read newest first within the range, of one Jenkins job or its branches"() {
        given:
        store.save([run(gui, hours(1), 'SUCCESS', 'DevSecOps/CERT/gui-full/develop'),
                    run(gui, hours(2), 'FAILURE', 'DevSecOps/CERT/gui-full'),
                    run(gui, hours(3), 'UNSTABLE', 'DevSecOps/CERT/gui-full-old'),
                    run(gui, hours(4), 'ABORTED'),
                    run(gui, Duration.ofDays(40), 'SUCCESS', 'DevSecOps/CERT/gui-full'),
                    run(guiUat, hours(1), 'SUCCESS')])

        expect:
        store.recentRuns(gui, null, 30, 25)*.result() == [RunResult.SUCCESS, RunResult.FAILURE, RunResult.UNSTABLE,
                                                         RunResult.ABORTED]
        store.recentRuns(gui, null, 30, 2)*.result() == [RunResult.SUCCESS, RunResult.FAILURE]
        store.recentRuns(gui, 'DevSecOps/CERT/gui-full', 90, 25)*.job() ==
                ['DevSecOps/CERT/gui-full/develop', 'DevSecOps/CERT/gui-full', 'DevSecOps/CERT/gui-full']
    }

    def "DORA points are read for every tag asked for within the range"() {
        given:
        store.save([dora(gui, hours(5), true), dora(gui, hours(2), false), dora(api, hours(1), true),
                    dora(guiUat, hours(1), true), dora(gui, Duration.ofDays(10), true),
                    run(gui, hours(1), 'SUCCESS')])

        expect:
        store.doraPoints([gui, api], 7) == [
                (gui): [new DoraPoint(NOW - hours(5), true, false, 3600, 600),
                        new DoraPoint(NOW - hours(2), false, false, 0, 600)],
                (api): [new DoraPoint(NOW - hours(1), true, false, 3600, 600)]]
        store.doraPoints([], 7) == [:]
    }

    def "the evidence of a run holds the points written during it"() {
        given:
        def finished = NOW - hours(1)
        def run = new PipelineRun(finished, RunResult.SUCCESS, 'develop', 7L, 600L, 'a1b2c3', null, 2L, 2L, 0L, 0L,
                0L, 0L)
        store.save([point('stage_event', gui, finished - Duration.ofSeconds(500), [stage: 'Build', status: 'pass', order: '1']),
                    point('stage_event', gui, finished - Duration.ofSeconds(900), [stage: 'Old', status: 'pass', order: '1']),
                    point('release_gate', gui, finished, [allowed: 'yes', violations: '0']),
                    point('code_coverage', gui, finished - Duration.ofSeconds(60), [module: 'gui', line_pct: '80']),
                    point('stage_event', guiUat, finished, [stage: 'Deploy', status: 'pass', order: '1'])])

        when:
        def evidence = store.evidenceOf([(gui): [run] as Set])

        then:
        evidence.keySet() == [run] as Set
        evidence[run].stages()*.name() == ['Build']
        evidence[run].releaseGate() == new ReleaseGateEvidence(true, 0L, null)
        evidence[run].coverage('gui').linePercent() == null
        store.evidenceOf([:]) == [:]
    }

    private static StoredPoint run(MetricsTag tag, Duration ago, String result, String job = null) {
        new StoredPoint('pipeline_run', tag, job, NOW - ago, [result: result, branch: 'develop', build: '7',
                                                             duration_s: '600'])
    }

    private static StoredPoint dora(MetricsTag tag, Duration ago, boolean deployment) {
        new StoredPoint('dora', tag, null, NOW - ago, [deployment: deployment ? '1' : '0', change_failure: '0',
                                                      lead_time_s: deployment ? '3600' : '0', duration_s: '600'])
    }

    private static StoredPoint point(String measurement, MetricsTag tag, Instant time, Map<String, String> values) {
        new StoredPoint(measurement, tag, null, time, values)
    }

    private static Duration hours(long hours) {
        Duration.ofHours(hours)
    }
}
