package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.domain.evidence.CoverageEvidence
import com.bbh.itss.dso.portal.domain.evidence.EvidenceLinks
import com.bbh.itss.dso.portal.domain.evidence.ReleaseGateEvidence
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.adapter.out.influx.InfluxRunEvidenceAdapter.belongsTo
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.WARN
import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.SUCCESS

class InfluxRunEvidenceAdapterSpec extends Specification {

    static final Instant FINISHED = Instant.parse('2026-10-04T10:00:00Z')

    InfluxQueryClient influx = Spy(constructorArgs: [
            new InfluxProperties('http://influx', 'DevSecOps', 'DORA-metrics', 't', '180d'), RestClient.builder()])

    def adapter = new InfluxRunEvidenceAdapter(influx)

    def gui = new MetricsTag('CERT-gui', 'test')
    def guiSast = new MetricsTag('CERT-guisast', 'test')
    def guiUat = new MetricsTag('CERT-gui', 'uat')

    def "the evidence of every run is read in one query, each run in its own window, and no runs need none"() {
        given:
        def runs = new LinkedHashMap<MetricsTag, Set<PipelineRun>>()
        runs.put(guiSast, [run(FINISHED.plusSeconds(3600), null)] as Set)
        runs.put(gui, [run(FINISHED, 600)] as Set)

        when:
        adapter.evidenceOf(runs)

        then:
        adapter.evidenceOf([:]) == [:]
        1 * influx.query('''\
            run0 = from(bucket: "DORA-metrics")
              |> range(start: time(v: "2026-10-04T09:49:56Z"), stop: time(v: "2026-10-04T10:00:02Z"))
              |> filter(fn: (r) => r.project == "CERT-gui" and r.env == "test")
              |> filter(fn: (r) => r._measurement == "security_findings" or r._measurement == "policy_status" or r._measurement == "code_coverage" or r._measurement == "test_execution" or r._measurement == "release_gate" or r._measurement == "vulnerabilities" or r._measurement == "stage_event" or r._measurement == "build_evidence")
              |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
              |> last()
            run1 = from(bucket: "DORA-metrics")
              |> range(start: time(v: "2026-10-04T10:59:56Z"), stop: time(v: "2026-10-04T11:00:02Z"))
              |> filter(fn: (r) => r.project == "CERT-guisast" and r.env == "test")
              |> filter(fn: (r) => r._measurement == "security_findings" or r._measurement == "policy_status" or r._measurement == "code_coverage" or r._measurement == "test_execution" or r._measurement == "release_gate" or r._measurement == "vulnerabilities" or r._measurement == "stage_event" or r._measurement == "build_evidence")
              |> filter(fn: (r) => not (r._measurement == "release_gate" and r._field == "allowed"))
              |> last()
            union(tables: [run0, run1])
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
            '''.stripIndent()) >> []
    }

    def "one run is read without a union, and a run with the same key in another environment in its own window"() {
        when:
        adapter.evidenceOf([(gui): [run(FINISHED, 60)] as Set])
        adapter.evidenceOf([(gui): [run(FINISHED, 600)] as Set, (guiUat): [run(FINISHED.minusSeconds(3600), 60)] as Set])

        then:
        1 * influx.query({ String flux ->
            flux.contains('range(start: time(v: "2026-10-04T09:58:56Z"), stop: time(v: "2026-10-04T10:00:02Z"))') &&
                    !flux.contains('union') && flux.endsWith('run0\n  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")\n')
        }) >> []
        1 * influx.query({ String flux ->
            flux.contains('r.project == "CERT-gui" and r.env == "test"') &&
                    flux.contains('r.project == "CERT-gui" and r.env == "uat"') &&
                    flux.contains('range(start: time(v: "2026-10-04T08:58:56Z"), stop: time(v: "2026-10-04T09:00:02Z"))')
        }) >> []
    }

    def "each row goes to the run of its project and environment when it was written during that run"() {
        given:
        def guiRun = run(FINISHED, 600)
        def sastRun = run(FINISHED, 300)
        def uatRun = run(FINISHED.minusSeconds(3600), 60)
        def runs = [(gui): [guiRun] as Set, (guiSast): [sastRun] as Set, (guiUat): [uatRun] as Set]
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
        evidence.keySet() == [guiRun, sastRun, uatRun] as Set
        evidence[guiRun].stages()*.name() == ['Build']
        evidence[guiRun].releaseGate() == new ReleaseGateEvidence(true, null, null)
        evidence[guiRun].coverage('gui') == CoverageEvidence.builder().status(WARN).build()
        evidence[uatRun].stages()*.name() == ['Deploy']
        evidence[sastRun].stages() == []
        evidence[sastRun].releaseGate() == null
    }

    def "the runs of services sharing a tag each get the points written during their own run"() {
        given:
        def earlier = run(FINISHED.minusSeconds(3600), 600)
        def later = run(FINISHED, 600)
        influx.query(_) >> [
                row('build_evidence', project: 'CERT-gui', env: 'test', _time: at(FINISHED.minusSeconds(3600)),
                        module: 'backend-api', artifact_version: '2.0.1'),
                row('build_evidence', project: 'CERT-gui', env: 'test', _time: at(FINISHED), module: 'gui',
                        artifact_version: '1.4.2')]

        when:
        def evidence = adapter.evidenceOf([(gui): [later, earlier] as Set])

        then:
        evidence[earlier].build(earlier, 'backend-api', links()).artifactVersion() == '2.0.1'
        evidence[earlier].build(earlier, 'gui', links()).artifactVersion() == null
        evidence[later].build(later, 'gui', links()).artifactVersion() == '1.4.2'
    }

    def "without InfluxDB no evidence is read and the reason is given"() {
        given:
        InfluxQueryClient client = Spy(constructorArgs: [
                new InfluxProperties(' ', 'DevSecOps', 'DORA-metrics', null, '365d'), RestClient.builder()])

        when:
        new InfluxRunEvidenceAdapter(client).evidenceOf([(gui): [run(FINISHED, 600)] as Set])

        then:
        0 * client.query(_)
        def e = thrown(UncheckedIOException)
        e.message == 'InfluxDB is not configured for the portal'
    }

    def "a row InfluxDB wrote with a time that is not one gives the reason"() {
        given:
        influx.query(_) >> [row('release_gate', project: 'CERT-gui', env: 'test', _time: 'yesterday')]

        when:
        adapter.evidenceOf([(gui): [run(FINISHED, 600)] as Set])

        then:
        def e = thrown(UncheckedIOException)
        e.message == "InfluxDB could not be read: Text 'yesterday' could not be parsed at index 0"
    }

    def "a #measurement point #offset seconds from the finish belongs to the run: #belongs"() {
        expect:
        belongsTo([_measurement: measurement, _time: at(FINISHED.plusSeconds(offset))], run(FINISHED, 600)) == belongs
        !belongsTo([_measurement: 'release_gate'], run(FINISHED, 600))

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
        null                | 0      || false
    }

    private static PipelineRun run(Instant finished, Long durationSeconds) {
        new PipelineRun(finished, SUCCESS, 'develop', 42L, durationSeconds, 'a1b2c3d',
                'DevSecOps/CERT/gui-full', 12L, 12L, 0L, 0L, 0L, 0L)
    }

    private static EvidenceLinks links() {
        EvidenceLinks.of(null, null, null, null, null, null)
    }

    private static Map<String, String> row(Map values, String measurement) {
        ([_measurement: measurement] + values).collectEntries { key, value -> [key, value as String] } as Map<String, String>
    }

    private static String at(Instant time) {
        time.toString()
    }
}
