package com.bbh.itss.dso.portal.adapter.out.localmetrics

import com.bbh.itss.dso.portal.domain.monitoring.MetricsRow
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.monitoring.RunResult.FAILURE
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static java.time.ZoneOffset.UTC
import static java.time.format.DateTimeFormatter.ofPattern

class RunHistorySpec extends Specification {

    static final Instant FROM = Instant.parse('2026-06-01T00:00:00Z')
    static final Instant UNTIL = Instant.parse('2026-10-01T00:00:00Z')
    static final MetricsTag TAG = new MetricsTag('PAY-gateway', 'test')
    static final String JOB = 'DevSecOps/PAY/gateway-full'
    static final Map<String, String> REPOSITORIES = [
            gateway: 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-gateway',
            ledger : 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-ledger']

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
        def starts = runs.collect { it.time().minusSeconds(it.values().duration_s as long).atZone(UTC) }

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
        NEXUS_IQ || ['Checkout', 'Build', 'Nexus IQ', 'Release Gate']
    }

    def "a #type run whose Nexus IQ stage warns records the golden pull request GoldenFix raised for each module"() {
        when:
        def runs = (1..400).collectMany { seed ->
            def points = new RunHistory(TAG, JOB, type, REPOSITORIES, new Random(seed)).points(FROM, UNTIL)
            def builds = points.findAll { it.measurement() == 'pipeline_run' }
                    .collectEntries { [it.time(), it.values().build] }
            def ends = points.findAll { it.measurement() == 'release_gate' }*.time()
            def nexusIq = points.findAll { it.measurement() == 'stage_event' && it.values().stage == 'Nexus IQ' }
            [ends, nexusIq*.values()*.status].transpose().collect { end, status ->
                [end: end, build: builds[end], warned: status == 'warn',
                 goldenFix: points.findAll { it.measurement() == 'goldenfix' && it.time() == end }*.values()]
            }
        }
        def warned = runs.findAll { it.warned }

        then:
        !warned.empty && warned.size() < runs.size()
        runs.findAll { !it.warned }.every { it.goldenFix.empty }
        warned.every { run ->
            run.goldenFix*.module == ['gateway', 'ledger'] && run.goldenFix.every {
                it.status == 'PR_CREATED' && it.pr_raised == '1' && it.build_check == '1' && it.build_failed == '0' &&
                        (it.applied as int) + (it.unresolved as int) == (it.offered as int) &&
                        (it.offered as int) in 2..4 && (it.unresolved as int) in 0..1 &&
                        it.pr_url == "${REPOSITORIES[it.module]}/pull-requests/${run.build}" &&
                        it.pr_title == 'GoldenFix-' + run.end.atZone(UTC).format(ofPattern('yyyyMMddHHmm'))
            }
        }

        where:
        type << [FULL, SECURITY, NEXUS_IQ]
    }

    def "the golden pull request is raised in the repository of the module and named after the time of its run"() {
        when:
        def points = history(NEXUS_IQ, 21).points(FROM, UNTIL)
        def warned = points.find { it.measurement() == 'release_gate' && it.values().reason.startsWith('Nexus IQ:') }
        def goldenFix = points.findAll { it.measurement() == 'goldenfix' }

        then:
        goldenFix*.time() == [warned.time()]
        goldenFix*.values() == [[module: 'gateway', status: 'PR_CREATED', offered: '4', applied: '4', unresolved: '0',
                                 pr_raised: '1', build_check: '1', build_failed: '0',
                                 pr_url: "${REPOSITORIES.gateway}/pull-requests/110".toString(),
                                 pr_title: 'GoldenFix-202609281050']]
    }

    def "a module without a Bitbucket repository records GoldenFix as not configured and raises no pull request"() {
        when:
        def goldenFix = new RunHistory(TAG, JOB, NEXUS_IQ, [gateway: null], new Random(21)).points(FROM, UNTIL)
                .findAll { it.measurement() == 'goldenfix' }

        then:
        goldenFix*.values() == [[module: 'gateway', status: 'NOT_CONFIGURED', offered: '4', applied: '0',
                                 unresolved: '0', pr_raised: '0', build_check: '0', build_failed: '0']]
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
                    (point.values().change_failure == '0' || run.result == FAILURE.name())
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
        new RunHistory(TAG, JOB, type, [gateway: REPOSITORIES.gateway], new Random(seed))
    }
}
