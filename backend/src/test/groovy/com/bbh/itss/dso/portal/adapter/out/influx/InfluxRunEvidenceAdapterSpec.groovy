package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.domain.evidence.CheckStatus
import com.bbh.itss.dso.portal.domain.evidence.CoverageEvidence
import com.bbh.itss.dso.portal.domain.evidence.ReleaseGateEvidence
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.MetricsUnavailableException
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class InfluxRunEvidenceAdapterSpec extends Specification {

    static final Instant FINISHED = Instant.parse('2026-10-04T10:00:00Z')

    InfluxQueryClient influx = Spy(constructorArgs: [
            new InfluxProperties('http://influx', 'DevSecOps', 'DORA-metrics', 't', '180d'), RestClient.builder()])

    @Subject
    def adapter = new InfluxRunEvidenceAdapter(influx)

    def gui = new MetricsTag('CERT-gui', 'test')
    def guiSast = new MetricsTag('CERT-guisast', 'test')
    def guiUat = new MetricsTag('CERT-gui', 'uat')

    def "no runs need no query"() {
        when:
        def evidence = adapter.evidenceOf([:])

        then:
        evidence == [:]
        0 * influx.query(_)
    }

    def "the evidence of every run is read in one query from the earliest start to the latest finish"() {
        given:
        def runs = new LinkedHashMap<MetricsTag, PipelineRun>()
        runs.put(gui, run(FINISHED, 600))
        runs.put(guiSast, run(FINISHED.plusSeconds(3600), null))
        runs.put(guiUat, run(FINISHED.minusSeconds(3600), 60))

        when:
        adapter.evidenceOf(runs)

        then:
        1 * influx.query('''\
            from(bucket: "DORA-metrics")
              |> range(start: time(v: "2026-10-04T08:58:56Z"), stop: time(v: "2026-10-04T11:00:02Z"))
              |> filter(fn: (r) => contains(value: r._measurement, set: ["security_findings", "policy_status", "code_coverage", "test_execution", "release_gate", "vulnerabilities", "stage_event"]))
              |> filter(fn: (r) => contains(value: r.project, set: ["CERT-gui", "CERT-guisast"]))
              |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
              |> last()
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
            '''.stripIndent()) >> []
    }

    def "the project names are escaped as Flux strings"() {
        when:
        adapter.evidenceOf([(new MetricsTag('CERT-"gui"', 'test')): run(FINISHED, 0)])

        then:
        1 * influx.query({ String flux -> flux.contains('set: ["CERT-\\"gui\\""]') }) >> []
    }

    def "each row goes to the run of its project and environment when it was written during that run"() {
        given:
        def runs = [(gui)    : run(FINISHED, 600), (guiSast): run(FINISHED, 300),
                    (guiUat) : run(FINISHED.minusSeconds(3600), 60)]
        influx.query(_) >> [
                row('release_gate', project: 'CERT-gui', env: 'test', _time: at(FINISHED), allowed: 'yes'),
                row('stage_event', project: 'CERT-gui', env: 'test', _time: at(FINISHED.minusSeconds(590)),
                        stage: 'Build', status: 'PASS', order: '1'),
                row('stage_event', project: 'CERT-gui', env: 'test', _time: at(FINISHED.minusSeconds(700)),
                        stage: 'Checkout', status: 'PASS', order: '0'),
                row('code_coverage', project: 'CERT-gui', env: 'test', _time: at(FINISHED.minusSeconds(3)),
                        module: 'gui', line_pct: '80', met: '1'),
                row('policy_status', project: 'CERT-gui', env: 'test', _time: at(FINISHED.plusSeconds(2)),
                        scanner: 'coverage', status: 'WARN'),
                row('stage_event', project: 'CERT-gui', env: 'uat', _time: at(FINISHED.minusSeconds(3600)),
                        stage: 'Deploy', status: 'PASS', order: '4'),
                row('stage_event', project: 'CERT-gui', env: 'prod', _time: at(FINISHED), stage: 'Release',
                        status: 'PASS'),
                row('stage_event', project: 'CERT-gui', env: 'test', stage: 'Undated', status: 'PASS')]

        when:
        def evidence = adapter.evidenceOf(runs)

        then:
        evidence.keySet() == [gui, guiSast, guiUat] as Set
        evidence[gui].stages()*.name() == ['Build']
        evidence[gui].releaseGate() == new ReleaseGateEvidence(true, null, null)
        evidence[gui].coverage('gui') == new CoverageEvidence(CheckStatus.WARN, null, null, null, null)
        evidence[guiUat].stages()*.name() == ['Deploy']
        evidence[guiSast].isEmpty()
    }

    def "without InfluxDB no evidence is read and the reason is given"() {
        given:
        InfluxQueryClient client = Spy(constructorArgs: [
                new InfluxProperties(' ', 'DevSecOps', 'DORA-metrics', null, '365d'), RestClient.builder()])

        when:
        new InfluxRunEvidenceAdapter(client).evidenceOf([(gui): run(FINISHED, 600)])

        then:
        0 * client.query(_)
        def e = thrown(MetricsUnavailableException)
        e.message == 'InfluxDB is not configured for the portal'
    }

    def "a row InfluxDB wrote with a time that is not one gives the reason"() {
        given:
        influx.query(_) >> [row('release_gate', project: 'CERT-gui', env: 'test', _time: 'yesterday')]

        when:
        adapter.evidenceOf([(gui): run(FINISHED, 600)])

        then:
        def e = thrown(MetricsUnavailableException)
        e.message == "InfluxDB could not be read: Text 'yesterday' could not be parsed at index 0"
    }

    def "a #measurement point #offset seconds from the finish belongs to the run: #belongs"() {
        expect:
        InfluxRunEvidenceAdapter.belongsTo([_measurement: measurement, _time: at(FINISHED.plusSeconds(offset))],
                run(FINISHED, 600)) == belongs

        where:
        measurement         | offset || belongs
        'release_gate'      | 0      || true
        'release_gate'      | -2     || true
        'release_gate'      | -3     || false
        'release_gate'      | 2      || true
        'release_gate'      | 3      || false
        'security_findings' | -300   || false
        'stage_event'       | -300   || true
        'stage_event'       | -604   || true
        'stage_event'       | -605   || false
        'stage_event'       | 2      || true
        'stage_event'       | 3      || false
    }

    def "a point without a time or a measurement belongs to no run"() {
        expect:
        !InfluxRunEvidenceAdapter.belongsTo([_measurement: 'release_gate'], run(FINISHED, 600))
        !InfluxRunEvidenceAdapter.belongsTo([_time: at(FINISHED)], run(FINISHED, 600))
    }

    def "a run started its duration before it finished, with slack for whole seconds"() {
        expect:
        InfluxRunEvidenceAdapter.startOf(run(FINISHED, 600)) == Instant.parse('2026-10-04T09:49:56Z')
        InfluxRunEvidenceAdapter.startOf(run(FINISHED, null)) == Instant.parse('2026-10-04T09:59:56Z')
    }

    private static PipelineRun run(Instant finished, Long durationSeconds) {
        new PipelineRun(finished, RunResult.SUCCESS, 'develop', 42L, durationSeconds, 'a1b2c3d',
                'DevSecOps/CERT/gui-full', 12L, 12L, 0L, 0L, 0L, 0L)
    }

    private static Map<String, String> row(Map values, String measurement) {
        ([_measurement: measurement] + values).collectEntries { key, value -> [key, value as String] } as Map<String, String>
    }

    private static String at(Instant time) {
        time.toString()
    }
}
