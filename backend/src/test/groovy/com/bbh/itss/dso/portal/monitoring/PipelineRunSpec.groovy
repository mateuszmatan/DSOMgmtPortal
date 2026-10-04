package com.bbh.itss.dso.portal.monitoring

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service

class PipelineRunSpec extends Specification {

    def "a run is read from a pivoted pipeline_run row"() {
        when:
        def run = PipelineRun.fromRow([_time       : '2026-10-01T10:00:00Z', result: 'unstable', branch: 'develop', build: '42',
                                       duration_s  : '1260.0', commit: 'a1b2c3', job: 'CERT/gui', stages_total: '12', passed: '11',
                                       warned      : '1', failed: '0', blocked: '0', skipped: ''])

        then:
        run == new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), RunResult.UNSTABLE, 'develop', 42, 1260, 'a1b2c3',
                'CERT/gui', 12, 11, 1, 0, 0, null)
    }

    def "missing text is null"() {
        when:
        def run = PipelineRun.fromRow([_time: '2026-10-01T10:00:00Z', branch: ' '])

        then:
        run.branch == null
        run.commit == null
        run.result == RunResult.NO_DATA
    }

    def "numbers are read leniently: '#value' is #number"() {
        expect:
        PipelineRun.number(value) == number

        where:
        value  || number
        null   || null
        ''     || null
        ' '    || null
        '12'   || 12
        '12.9' || 12
        'n/a'  || null
    }

    def "the result tag '#tag' is #result"() {
        expect:
        RunResult.fromTag(tag) == result

        where:
        tag         || result
        null        || RunResult.NO_DATA
        'SUCCESS'   || RunResult.SUCCESS
        ' failure ' || RunResult.FAILURE
        'unstable'  || RunResult.UNSTABLE
        'ABORTED'   || RunResult.ABORTED
        'NOT_BUILT' || RunResult.NOT_BUILT
        'RUNNING'   || RunResult.NO_DATA
    }

    def "a pipeline's status is its latest run unless it is disabled"() {
        given:
        def enabled = pipeline(service(product()))
        def disabled = pipeline(service(product()))
        disabled.revokeActiveKey('retired')
        def failed = PipelineRun.fromRow([_time: '2026-10-01T10:00:00Z', result: 'FAILURE'])

        expect:
        RunResult.of(enabled, failed) == RunResult.FAILURE
        RunResult.of(enabled, null) == RunResult.NO_DATA
        RunResult.of(disabled, failed) == RunResult.DISABLED
    }

    def "the worst of #statuses is #worst"() {
        expect:
        RunResult.worst(statuses) == worst

        where:
        statuses                                                   || worst
        []                                                         || RunResult.NO_DATA
        [RunResult.SUCCESS, RunResult.FAILURE, RunResult.NO_DATA]  || RunResult.FAILURE
        [RunResult.ABORTED, RunResult.UNSTABLE]                    || RunResult.UNSTABLE
        [RunResult.SUCCESS, RunResult.DISABLED]                    || RunResult.SUCCESS
        [RunResult.DISABLED, RunResult.NO_DATA]                    || RunResult.NO_DATA
        [RunResult.DISABLED]                                       || RunResult.DISABLED
        [RunResult.NOT_BUILT, RunResult.SUCCESS]                   || RunResult.NOT_BUILT
    }
}
