package com.bbh.itss.dso.portal.domain.evidence

import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.BLOCKED
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.FAIL
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.NOT_REQUIRED
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.NO_DATA
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.PASS
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.SKIP
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.WARN
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.DAST
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.SAST
import static com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner.SONARQUBE

class RunEvidenceSpec extends Specification {

    static final String APPSCAN = 'https://bbh.cloud.appscan.com/main/myapps/app-1/scans'
    static final String SONAR = 'https://tools.bbh.com/sonar/dashboard?id=cert-gui'
    static final String NEXUS_IQ_URL = 'https://tools.bbh.com/IQ/'

    def links = EvidenceLinks.of('https://jenkins.test/job/gui/42/', 'https://bbh.cloud.appscan.com', 'app-1',
            'https://tools.bbh.com/sonar', 'cert-gui', 'https://tools.bbh.com/IQ')

    def "the evidence copies its points, is empty without them and reads the library's measurements"() {
        given:
        List<EvidencePoint> points = []
        def evidence = new RunEvidence(points)

        when:
        points << point('release_gate', allowed: 'yes')

        then:
        evidence.isEmpty()
        evidence.releaseGate() == null
        new RunEvidence([point('release_gate', allowed: 'yes')]).stages() == []
        new RunEvidence([point('stage_event', stage: 'Build')]).releaseGate() == null
        RunEvidence.none().isEmpty()
        !new RunEvidence([point('stage_event', stage: 'Build')]).isEmpty()
        RunEvidence.MEASUREMENTS == ['security_findings', 'policy_status', 'code_coverage', 'test_execution',
                                   'release_gate', 'vulnerabilities', 'stage_event', 'build_evidence']
    }

    def "coverage met #met, measured #measured and policy status #policy is #status"() {
        given:
        List<EvidencePoint> rows = [point('code_coverage', [module: 'gui', line_pct: '70', required: '60'] +
                (met == null ? [:] : [met: met]) + (measured == null ? [:] : [measured: measured]))]
        if (policy != null) {
            rows << point('policy_status', scanner: 'coverage', status: policy)
        }

        expect:
        new RunEvidence(rows).coverage('gui').status() == status

        where:
        met   | measured | policy        || status
        '1'   | 'yes'    | null          || PASS
        '1'   | null     | null          || PASS
        '2.0' | 'yes'    | null          || PASS
        '0'   | 'yes'    | null          || WARN
        '0.0' | 'yes'    | null          || WARN
        null  | 'yes'    | null          || WARN
        'n/a' | 'yes'    | null          || WARN
        '1'   | 'no'     | null          || SKIP
        '1'   | 'yes'    | 'FAIL'        || FAIL
        '0'   | 'yes'    | 'pass'        || PASS
        '0'   | 'no'     | 'NOT_REQUIRED'|| NOT_REQUIRED
        '1'   | 'yes'    | 'unknown'     || PASS
    }

    def "coverage read from #rows.size() rows is #evidence.status()"() {
        expect:
        new RunEvidence(rows).coverage('gui') == evidence

        where:
        rows << [[point('code_coverage', module: 'gui', measured: 'yes', line_pct: '82.5', required: '60.0', covered: '825',
                        total: '1000', met: '1'),
                  point('code_coverage', module: 'backend-api', measured: 'yes', line_pct: '40.0', required: '60.0',
                        covered: '40', total: '100', met: '0')],
                 [point('code_coverage', module: 'gui', measured: 'yes', line_pct: '40.0', required: '60.0',
                        covered: '40', total: '100', met: '0')],
                 [point('code_coverage', module: 'gui', measured: 'no', line_pct: '0.0', required: '60', covered: '0',
                        total: '0', met: '0')],
                 [],
                 [point('policy_status', scanner: 'coverage', status: 'WARN'),
                  point('policy_status', scanner: 'sast', status: 'FAIL')],
                 [point('code_coverage', module: '', line_pct: '91', required: '60', met: '1', covered: '91',
                        total: '100')],
                 [point('code_coverage', module: 'batch', line_pct: '91', met: '1'),
                  point('code_coverage', module: 'backend-api', line_pct: '50', met: '0')]]
        evidence << [new CoverageEvidence(PASS, 82.5d, 60.0d, 825L, 1000L),
                     new CoverageEvidence(WARN, 40.0d, 60.0d, 40L, 100L),
                     new CoverageEvidence(SKIP, null, 60.0d, null, null),
                     new CoverageEvidence(NO_DATA, null, null, null, null),
                     new CoverageEvidence(WARN, null, null, null, null),
                     new CoverageEvidence(PASS, 91.0d, 60.0d, 91L, 100L),
                     new CoverageEvidence(NO_DATA, null, null, null, null)]
    }

    def "each suite is read from the module's test jobs and the stage that ran them"() {
        given:
        def points = new RunEvidence([
                point('test_execution', module: 'gui', suite: 'Smoke tests', total: '5', passed: '4', failed: '0',
                        not_configured: '1', duration_ms: '12345.0'),
                point('test_execution', module: 'gui', suite: 'Regression tests (>60% user stories coverage)', total: '10',
                        passed: '8', failed: '2', not_configured: '0', duration_ms: '600000'),
                point('test_execution', module: 'backend-api', suite: 'Smoke tests', total: '3', passed: '0', failed: '3'),
                point('stage_event', stage: 'Performance tests', status: 'SKIP', order: '9')])

        expect:
        points.testSuites('gui') == [
                new TestSuiteEvidence(TestSuite.UNIT, NO_DATA, null, null, null, null, null, null),
                new TestSuiteEvidence(TestSuite.SMOKE, PASS, 5L, 4L, 0L, null, 1L, 12345L),
                new TestSuiteEvidence(TestSuite.REGRESSION, WARN, 10L, 8L, 2L, null, 0L, 600000L),
                new TestSuiteEvidence(TestSuite.PERFORMANCE, SKIP, null, null, null, null, null, null)]
    }

    def "the unit tests are read from the module's unit suite and the stage that ran them"() {
        given:
        def points = new RunEvidence([
                point('test_execution', module: 'gui', suite: 'unit', total: '120', passed: '115', failed: '2',
                        skipped: '3', not_configured: '0', duration_ms: '45000', success_rate: '95.83'),
                point('test_execution', module: 'backend-api', suite: 'unit', total: '80', passed: '80', failed: '0'),
                point('stage_event', stage: 'Unit tests', status: 'WARN', order: '2')])

        expect:
        points.testSuites('gui')[0] == new TestSuiteEvidence(TestSuite.UNIT, WARN, 120L, 115L, 2L, 3L, 0L, 45000L)
        points.testSuites('backend-api')[0].with { [status(), total(), skipped()] } == [WARN, 80L, null]
        new RunEvidence([point('test_execution', module: 'gui', suite: 'unit', total: '4', passed: '4', failed: '0',
                skipped: '0')]).testSuites('gui')[0].status() == PASS
    }

    def "the status of the stage that ran the tests wins over the test counts"() {
        given:
        def points = new RunEvidence([
                point('test_execution', module: 'gui', suite: 'Smoke tests', total: '2', passed: '2', failed: '0'),
                point('stage_event', stage: 'Smoke tests', status: 'FAIL', order: '5'),
                point('test_execution', module: 'gui', suite: 'Performance tests', total: '1', passed: '0', failed: '1'),
                point('stage_event', stage: 'PERFORMANCE TESTS', status: 'pass', order: '7')])

        when:
        def suites = points.testSuites('gui')

        then:
        suites*.status == [NO_DATA, FAIL, NO_DATA, PASS]
        suites[1].passed() == 2L
        suites[3].failed() == 1L
    }

    def "a suite without a failure count passes"() {
        expect:
        new RunEvidence([point('test_execution', module: 'gui', suite: 'smoke', total: '1', passed: '1', failed: failedCount)])
                .testSuites('gui')[1].with { [status(), failed()] } == [PASS, null]

        where:
        failedCount << [null, '', 'none']
    }

    def "test jobs of other modules or without a suite are not read, but the only row of a suite without a module is"() {
        given:
        def points = new RunEvidence([
                point('test_execution', module: 'backend-api', suite: 'Smoke tests', total: '3', failed: '0'),
                point('test_execution', module: 'batch', suite: 'Smoke tests', total: '4', failed: '0'),
                point('test_execution', module: 'gui', total: '9', failed: '0'),
                point('test_execution', suite: 'Regression tests', total: '7', passed: '7', failed: '0'),
                point('test_execution', module: 'backend-api', suite: 'unit', total: '80', passed: '80')])

        def none = TestSuite.values().collect { new TestSuiteEvidence(it, NO_DATA, null, null, null, null, null, null) }

        expect:
        new RunEvidence([]).testSuites('gui') == none
        points.testSuites('gui') == [none[0], none[1],
                                     new TestSuiteEvidence(TestSuite.REGRESSION, PASS, 7L, 7L, 0L, null, null, null),
                                     none[3]]
    }

    def "a run without scans reports each scanner without data, with its link, unless the policy did not require it"() {
        expect:
        new RunEvidence([]).scans('gui', links) == [
                new ScanEvidence(SAST, NO_DATA, null, null, null, null, null, null, null, null, APPSCAN),
                new ScanEvidence(DAST, NO_DATA, null, null, null, null, null, null, null, null, APPSCAN),
                new ScanEvidence(SONARQUBE, NO_DATA, null, null, null, null, null, null, null, null, SONAR),
                new ScanEvidence(NEXUS_IQ, NO_DATA, null, null, null, null, null, null, null, null, NEXUS_IQ_URL)]
        new RunEvidence([]).scans('gui', EvidenceLinks.of(null, null, null, null, null, null))*.link() ==
                [null, null, null, null]
        new RunEvidence([point('policy_status', scanner: 'dast', status: 'NOT_REQUIRED'),
                         point('policy_status', scanner: 'iast', status: 'SKIP')]).scans('gui', links)*.status() ==
                [NO_DATA, NOT_REQUIRED, NO_DATA, SKIP]
    }

    def "findings are read against the limits, Nexus IQ and SonarQube counts from the vulnerabilities"() {
        given:
        def points = new RunEvidence([
                point('security_findings', module: 'gui', scanner: 'sast', status: 'WARN', critical: '0', high: '2',
                        medium: '5', low: '7', max_critical: '0', max_high: '0', max_medium: '10'),
                point('security_findings', module: 'gui', scanner: 'dast', status: 'PASS', critical: '0', high: '0',
                        medium: '1', low: '3', max_critical: '0', max_high: '1', max_medium: '5'),
                point('security_findings', module: 'gui', scanner: 'niq', status: 'FAIL', critical: '0', high: '0',
                        medium: '0', low: '0', max_critical: '0', max_high: '2', max_medium: '8'),
                point('vulnerabilities', scanner: 'nexusiq', critical: '1', high: '3', medium: '4', low: '6'),
                point('vulnerabilities', scanner: 'sonar', critical: '0', high: '1', medium: '12', low: '40'),
                point('policy_status', scanner: 'sast', status: 'BLOCKED'),
                point('policy_status', scanner: 'sonar', status: 'PASS'),
                point('policy_status', scanner: 'iast', status: 'FAIL')])

        expect:
        points.scans('gui', links) == [
                new ScanEvidence(SAST, BLOCKED, 0L, 2L, 5L, 7L, 0L, 0L, 10L, null, APPSCAN),
                new ScanEvidence(DAST, PASS, 0L, 0L, 1L, 3L, 0L, 1L, 5L, null, APPSCAN),
                new ScanEvidence(SONARQUBE, PASS, 0L, 1L, 12L, 40L, null, null, null, null, SONAR),
                new ScanEvidence(NEXUS_IQ, FAIL, 1L, 3L, 4L, 6L, 0L, 2L, 8L, null, NEXUS_IQ_URL)]
    }

    def "the reports the build recorded replace the links the portal builds"() {
        given:
        def points = new RunEvidence([
                point('build_evidence', module: 'gui', sast_report_url: 'https://jenkins.test/job/gui/42/artifact/sast.html',
                        dast_report_url: '', nexusiq_report_url: 'https://tools.bbh.com/IQ/report/gui/abc',
                        sonar_report_url: 'https://tools.bbh.com/sonar/dashboard?id=cert-gui&branch=develop'),
                point('build_evidence', module: 'backend-api', sast_report_url: 'https://jenkins.test/other.html')])

        expect:
        points.scans('gui', links)*.link() == ['https://jenkins.test/job/gui/42/artifact/sast.html', APPSCAN,
                                               'https://tools.bbh.com/sonar/dashboard?id=cert-gui&branch=develop',
                                               'https://tools.bbh.com/IQ/report/gui/abc']
    }

    def "the build evidence of another service of a shared tag is not read as the service's"() {
        given:
        def other = new RunEvidence([point('build_evidence', module: 'backend-api', artifact_version: '2.0.1',
                sonar_quality_gate: 'ERROR', config_sha256: '0123456789abcdef'),
                point('test_execution', module: 'backend-api', suite: 'unit', total: '80', passed: '80')])
        def run = new PipelineRun(Instant.parse('2026-10-04T10:00:00Z'), RunResult.SUCCESS, 'develop', 42, 600,
                'a1b2c3d4e5f6', 'DevSecOps/CertScanner-pipeline', null, null, null, null, null, null)

        expect:
        other.build(run, 'gui', links).with { [artifactVersion(), configSha256()] } == [null, null]
        other.scans('gui', links)[2].with { [status(), qualityGate()] } == [NO_DATA, null]
        other.testSuites('gui')[0].total() == null
    }

    def "the Sonar quality gate #gate with policy status #policy reads as #status"() {
        given:
        List<EvidencePoint> rows = [point('build_evidence', [module: 'gui'] + (gate == null ? [:] : [sonar_quality_gate: gate]))]
        if (policy != null) {
            rows << point('policy_status', scanner: 'sonar', status: policy)
        }

        expect:
        new RunEvidence(rows).scans('gui', links)[2].with { [it.status(), it.qualityGate()] } == [status, shown]

        where:
        gate    | policy || status  | shown
        'OK'    | null   || PASS    | 'OK'
        'WARN'  | null   || WARN    | 'WARN'
        'ERROR' | null   || FAIL    | 'ERROR'
        'NONE'  | null   || NO_DATA | null
        null    | null   || NO_DATA | null
        'ERROR' | 'PASS' || PASS    | 'ERROR'
    }

    def "findings of the module are chosen among several, and a single row is taken only without a module"() {
        given:
        def several = new RunEvidence([
                point('security_findings', module: 'backend-api', scanner: 'sast', status: 'FAIL', critical: '4'),
                point('security_findings', module: 'gui', scanner: 'sast', status: 'PASS', critical: '0'),
                point('security_findings', module: 'batch', scanner: 'dast', status: 'FAIL', critical: '1'),
                point('security_findings', module: 'reports', scanner: 'dast', status: 'FAIL', critical: '2')])
        def single = new RunEvidence([
                point('security_findings', scanner: 'sast', status: 'warn', critical: '1'),
                point('security_findings', module: 'backend-api', scanner: 'dast', status: 'FAIL', critical: '3')])

        expect:
        several.scans('gui', links)[0].with { [status(), critical()] } == [PASS, 0L]
        several.scans('gui', links)[1].with { [status(), critical()] } == [NO_DATA, null]
        single.scans('gui', links)[0].with { [status(), critical()] } == [WARN, 1L]
        single.scans('gui', links)[1].with { [status(), critical()] } == [NO_DATA, null]
    }

    def "the release gate tag allowed #allowed with violations #violations and reason #reason reads as #evidence"() {
        expect:
        new RunEvidence([point('release_gate', [allowed: allowed, violations: violations, reason: reason]
                .findAll { it.value != null })]).releaseGate() == evidence

        where:
        allowed | violations | reason                 || evidence
        'yes'   | '0'        | ''                     || new ReleaseGateEvidence(true, 0L, null)
        '1'     | null       | null                   || new ReleaseGateEvidence(true, null, null)
        'no'    | '3.0'      | 'SAST limits exceeded' || new ReleaseGateEvidence(false, 3L, 'SAST limits exceeded')
        '0'     | '1'        | '  '                   || new ReleaseGateEvidence(false, 1L, null)
        null    | null       | null                   || new ReleaseGateEvidence(false, null, null)
    }

    def "the stages are listed in the order they ran, unknown positions last"() {
        given:
        def points = new RunEvidence([
                point('stage_event', stage: 'SAST', status: 'WARN', order: '3', duration_s: '300',
                        reason: 'High findings above the limit'),
                point('stage_event', stage: 'Cleanup', status: 'something', duration_s: ''),
                point('stage_event', stage: 'Build', status: 'PASS', order: '1', duration_s: '65.9'),
                point('stage_event', stage: 'Unit tests', status: 'pass', order: '2.0', duration_s: '120', reason: ' '),
                point('policy_status', scanner: 'sast', status: 'WARN')])

        expect:
        points.stages() == [
                new StageEvidence('Build', PASS, 65L, null),
                new StageEvidence('Unit tests', PASS, 120L, null),
                new StageEvidence('SAST', WARN, 300L, 'High findings above the limit'),
                new StageEvidence('Cleanup', NO_DATA, null, null)]
    }

    def "the report of a run holds its build with links and every kind of evidence of the module"() {
        given:
        def run = new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), RunResult.UNSTABLE, 'main', 42L, 900L,
                'abc123', 'CERT/gui', 10L, 8L, 1L, 1L, 0L, 0L)
        def evidence = new RunEvidence([
                point('code_coverage', module: 'gui', line_pct: '82.5', required: '60', met: '1', covered: '825',
                        total: '1000'),
                point('release_gate', allowed: 'yes', violations: '0'),
                point('stage_event', stage: 'Build', status: 'PASS', order: '1', duration_s: '60')])

        when:
        def report = evidence.report(run, 'gui', links)

        then:
        report.build() == new BuildEvidence(42L, run.time(), RunResult.UNSTABLE, 'main', 'abc123', null, 900L,
                'CERT/gui', 'https://jenkins.test/job/gui/42/', 'https://jenkins.test/job/gui/42/Pipeline_20Report/',
                'https://jenkins.test/job/gui/42/testReport/', 'https://jenkins.test/job/gui/42/artifact/', null, null)
        report.coverage() == evidence.coverage('gui')
        report.testSuites() == evidence.testSuites('gui')
        report.scans() == evidence.scans('gui', links)
        report.releaseGate() == new ReleaseGateEvidence(true, 0L, null)
        report.stages() == [new StageEvidence('Build', PASS, 60L, null)]
    }

    def "the build names the artifact version and the portal configuration it ran with"() {
        given:
        def run = new PipelineRun(Instant.parse('2026-10-01T10:00:00Z'), RunResult.SUCCESS, 'main', 43L, 600L,
                'def456', 'CERT/gui', 10L, 10L, 0L, 0L, 0L, 0L)
        def evidence = new RunEvidence([
                point('build_evidence', module: 'gui', artifact_version: '1.4.2-43', sonar_quality_gate: 'OK',
                        config_rendered_at: renderedAt, config_sha256: '9f86d081884c7d65')])

        expect:
        evidence.build(run, 'gui', links).with { [artifactVersion(), configRenderedAt(), configSha256()] } ==
                ['1.4.2-43', rendered, '9f86d081884c7d65']

        where:
        renderedAt                 || rendered
        '2026-10-01T09:58:12.345Z' || Instant.parse('2026-10-01T09:58:12.345Z')
        'yesterday'                || null
        ' '                        || null
    }

    def "a point holds a copy of its values and needs a measurement"() {
        given:
        def values = [stage: 'Build', reason: null]
        def point = new EvidencePoint('stage_event', values)

        when:
        values.stage = 'Deploy'

        then:
        point.value('stage') == 'Build'
        point.value('reason') == null
        point.value('missing') == null

        when:
        new EvidencePoint(null, [:])

        then:
        def e = thrown(NullPointerException)
        e.message == 'a point belongs to a measurement'
    }

    static EvidencePoint point(Map values, String measurement) {
        point(measurement, values)
    }

    static EvidencePoint point(String measurement, Map values) {
        new EvidencePoint(measurement,
                values.collectEntries { key, value -> [key, value as String] } as Map<String, String>)
    }
}
