package com.bbh.itss.dso.portal.adapter.out.localmetrics

import com.bbh.itss.dso.portal.domain.monitoring.MetricsRow
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY

class RunHistorySpec extends Specification {

    static final Instant FROM = Instant.parse('2026-06-01T00:00:00Z')
    static final Instant UNTIL = Instant.parse('2026-10-01T00:00:00Z')
    static final MetricsTag TAG = new MetricsTag('PAY-gateway', 'test')
    static final String JOB = 'DevSecOps/PAY/gateway-full'

    def "the same seed gives the same history"() {
        expect:
        history(FULL, 7).points(FROM, UNTIL) == history(FULL, 7).points(FROM, UNTIL)
        history(FULL, 7).points(FROM, UNTIL) != history(FULL, 8).points(FROM, UNTIL)
    }

    def "every run in the window is a pipeline run with a DORA point, and the last two runs carry evidence"() {
        when:
        def points = history(FULL, 1).points(FROM, UNTIL)
        def runs = points.findAll { it.measurement() == 'pipeline_run' }

        then:
        runs.size() > 80
        points.every { it.tag() == TAG && it.job() == JOB && !it.time().isBefore(FROM) && !it.time().isAfter(UNTIL) }
        points.findAll { it.measurement() == 'dora' }*.time() == runs*.time()
        points.count { it.measurement() == 'stage_event' } == 24
        points.count { it.measurement() == 'release_gate' } == 2
        points.findAll { it.measurement() == 'build_evidence' }*.values()*.module == ['gateway', 'gateway']
        runs.collect { MetricsRow.run(it.row()) }.every { it.durationSeconds() >= 45 * 60 && it.job() == JOB }
        (runs*.values()*.result as Set).containsAll(['SUCCESS', 'FAILURE'])
    }

    def "runs start in working hours, mostly on weekdays"() {
        when:
        def runs = history(SECURITY, 3).points(FROM, UNTIL).findAll { it.measurement() == 'pipeline_run' }
        def starts = runs.collect { it.time().minusSeconds(it.values().duration_s as long).atZone(ZoneOffset.UTC) }

        then:
        starts.every { it.hour >= 6 && it.hour < 21 }
        starts.count { it.dayOfWeek.value >= 6 } < starts.size() / 5
    }

    def "a #type pipeline runs #stages"() {
        when:
        def points = history(type, 5).points(FROM, UNTIL)

        then:
        points.findAll { it.measurement() == 'stage_event' }*.values()*.stage.unique() == stages

        where:
        type     || stages
        FULL     || ['Checkout', 'Build', 'Unit Tests', 'SonarQube', 'AppScan SAST', 'Nexus IQ', 'Publish Artifact',
                     'Deploy RD', 'Smoke Tests', 'Regression Tests', 'Deploy QC', 'Release Gate']
        SECURITY || ['Checkout', 'Build', 'Unit Tests', 'AppScan SAST', 'Nexus IQ', 'SonarQube', 'AppScan DAST',
                     'Release Gate']
        EXTENDED || ['Checkout', 'Read Security Run', 'Deploy RD', 'Smoke Tests', 'Regression Tests',
                     'Performance Tests', 'AppScan DAST', 'Deploy QC', 'Release Gate']
        SAST     || ['Checkout', 'Build', 'AppScan SAST', 'Release Gate']
    }

    def "a run deploys only from a branch that is not a feature branch and only a failed deployment is a change failure"() {
        when:
        def points = history(FULL, 11).points(FROM, UNTIL)
        def runs = points.findAll { it.measurement() == 'pipeline_run' }.collectEntries { [it.time(), it.values()] }
        def dora = points.findAll { it.measurement() == 'dora' }

        then:
        dora.any { it.values().deployment == '1' }
        dora.every { point ->
            def run = runs[point.time()]
            (point.values().deployment == '0' || !run.branch.startsWith('feature/')) &&
                    (point.values().change_failure == '0' || run.result == RunResult.FAILURE.name())
        }
    }

    def "a failed run skips every stage after the one that failed and a run without failure passes its release gate"() {
        when:
        def points = history(SAST, 13).points(FROM, UNTIL)
        def gates = points.findAll { it.measurement() == 'release_gate' }
        def stages = points.findAll { it.measurement() == 'stage_event' }*.values()

        then:
        gates.size() == 2
        stages.collate(4).every { run ->
            int failed = run.findIndexOf { it.status == 'fail' }
            failed < 0 || run.drop(failed + 1).every { it.status == 'skip' && it.duration_s == '0' }
        }
        stages.findAll { it.status in ['fail', 'warn'] }.every { it.reason }
    }

    private static RunHistory history(PipelineType type, long seed) {
        new RunHistory(TAG, JOB, type, ['gateway'], new Random(seed))
    }
}
