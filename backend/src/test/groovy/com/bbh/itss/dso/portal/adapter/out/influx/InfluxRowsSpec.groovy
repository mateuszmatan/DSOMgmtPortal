package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import spock.lang.Specification

import java.time.Instant

class InfluxRowsSpec extends Specification {

    def "a run is read from a pivoted pipeline_run row"() {
        when:
        def run = InfluxRows.run([_time       : '2026-10-01T10:00:00Z', result: 'unstable', branch: 'develop',
                                  build       : '42', duration_s: '1260.0', commit: 'a1b2c3', job: 'CERT/gui',
                                  stages_total: '12', passed: '11', warned: '1', failed: '0', blocked: '0',
                                  skipped     : ''])

        then:
        run == new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), RunResult.UNSTABLE, 'develop', 42, 1260,
                'a1b2c3', 'CERT/gui', 12, 11, 1, 0, 0, null)
    }

    def "missing text is null"() {
        when:
        def run = InfluxRows.run([_time: '2026-10-01T10:00:00Z', branch: ' '])

        then:
        run.branch() == null
        run.commit() == null
        run.result() == RunResult.NO_DATA
    }

    def "the tag of a row is its project and environment"() {
        expect:
        InfluxRows.tag([project: 'CERT-gui', env: 'test', _time: '2026-10-01T10:00:00Z']) ==
                new MetricsTag('CERT-gui', 'test')
    }

    def "a DORA point is read from a pivoted dora row, a row without a time is none"() {
        expect:
        InfluxRows.doraPoint([_time: '2026-10-01T10:00:00Z', deployment: '1.0', change_failure: '0',
                              lead_time_s: '3600', duration_s: '600.0']) ==
                new DoraPoint(Instant.parse('2026-10-01T10:00:00Z'), true, false, 3600, 600)
        InfluxRows.doraPoint([_time: '2026-10-02T10:00:00Z', change_failure: 'yes']) ==
                new DoraPoint(Instant.parse('2026-10-02T10:00:00Z'), false, false, 0, 0)
        InfluxRows.doraPoint([deployment: '1']) == null
    }

    def "numbers are read leniently: '#value' is #number"() {
        expect:
        InfluxRows.number(value) == number

        where:
        value  || number
        null   || null
        ''     || null
        ' '    || null
        '12'   || 12
        '12.9' || 12
        'n/a'  || null
    }
}
