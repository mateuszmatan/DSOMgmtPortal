package com.bbh.itss.dso.portal.domain.monitoring

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey

class RunResultSpec extends Specification {

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
        def enabled = pipeline()
        def disabled = pipeline(keys: [revokedKey(reason: 'retired')])
        def failed = new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), RunResult.FAILURE, null, null, null, null,
                null, null, null, null, null, null, null)

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

    def "a run is known by the time it finished"() {
        when:
        new PipelineRun(null, RunResult.SUCCESS, null, null, null, null, null, null, null, null, null, null, null)

        then:
        thrown(NullPointerException)
    }
}
