package com.bbh.itss.dso.portal.evidence

import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.CoverageEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ReleaseGateEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ScanEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.StageEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.TestSuiteEvidence
import spock.lang.Specification

import static com.bbh.itss.dso.portal.evidence.CheckStatus.BLOCKED
import static com.bbh.itss.dso.portal.evidence.CheckStatus.FAIL
import static com.bbh.itss.dso.portal.evidence.CheckStatus.NOT_REQUIRED
import static com.bbh.itss.dso.portal.evidence.CheckStatus.NO_DATA
import static com.bbh.itss.dso.portal.evidence.CheckStatus.PASS
import static com.bbh.itss.dso.portal.evidence.CheckStatus.SKIP
import static com.bbh.itss.dso.portal.evidence.CheckStatus.WARN
import static com.bbh.itss.dso.portal.evidence.EvidenceScanner.DAST
import static com.bbh.itss.dso.portal.evidence.EvidenceScanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.evidence.EvidenceScanner.SAST
import static com.bbh.itss.dso.portal.evidence.EvidenceScanner.SONARQUBE

class RunPointsSpec extends Specification {

    static final String APPSCAN = 'https://bbh.cloud.appscan.com/main/myapps/app-1/scans'
    static final String SONAR = 'https://tools.bbh.com/sonar/dashboard?id=cert-gui'
    static final String NEXUS_IQ_URL = 'https://tools.bbh.com/IQ/'

    def links = EvidenceLinks.of('https://jenkins.test/job/gui/', 42L, 'https://bbh.cloud.appscan.com', 'app-1',
            'https://tools.bbh.com/sonar', 'cert-gui', 'https://tools.bbh.com/IQ')

    def "the points of a run are empty without rows and the measurements read are the library's"() {
        expect:
        new RunPoints([]).isEmpty()
        !new RunPoints([row('stage_event', stage: 'Build')]).isEmpty()
        RunPoints.MEASUREMENTS == ['security_findings', 'policy_status', 'code_coverage', 'test_execution',
                                   'release_gate', 'vulnerabilities', 'stage_event']
    }

    def "the rows are copied, so later changes to the list do not change the points"() {
        given:
        List<Map<String, String>> rows = []
        def points = new RunPoints(rows)

        when:
        rows << row('release_gate', allowed: 'yes')

        then:
        points.isEmpty()
        points.releaseGate() == null
    }

    def "the module's line coverage is read against the required minimum"() {
        given:
        def points = new RunPoints([
                row('code_coverage', module: 'gui', measured: 'yes', line_pct: '82.5', required: '60.0', covered: '825',
                        total: '1000', met: '1'),
                row('code_coverage', module: 'backend-api', measured: 'yes', line_pct: '40.0', required: '60.0',
                        covered: '40', total: '100', met: '0')])

        expect:
        points.coverage('gui') == new CoverageEvidence(PASS, 82.5d, 60.0d, 825L, 1000L)
        points.coverage('backend-api') == new CoverageEvidence(WARN, 40.0d, 60.0d, 40L, 100L)
    }

    def "coverage met #met, measured #measured and policy status #policy is #status"() {
        given:
        List<Map<String, String>> rows = [row('code_coverage', [module: 'gui', line_pct: '70', required: '60'] +
                (met == null ? [:] : [met: met]) + (measured == null ? [:] : [measured: measured]))]
        if (policy != null) {
            rows << row('policy_status', scanner: 'coverage', status: policy)
        }

        expect:
        new RunPoints(rows).coverage('gui').status() == status

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

    def "coverage that was not measured keeps only the required minimum"() {
        given:
        def points = new RunPoints([row('code_coverage', module: 'gui', measured: 'no', line_pct: '0.0', required: '60',
                covered: '0', total: '0', met: '0')])

        expect:
        points.coverage('gui') == new CoverageEvidence(SKIP, null, 60.0d, null, null)
    }

    def "a run without coverage has no data, unless the policy reported it"() {
        expect:
        new RunPoints([]).coverage('gui') == new CoverageEvidence(NO_DATA, null, null, null, null)
        new RunPoints([row('policy_status', scanner: 'coverage', status: 'WARN'),
                       row('policy_status', scanner: 'sast', status: 'FAIL')]).coverage('gui') ==
                new CoverageEvidence(WARN, null, null, null, null)
    }

    def "a single coverage row is read even when the run named its module differently"() {
        expect:
        new RunPoints([row('code_coverage', module: '', line_pct: '91', required: '60', met: '1', covered: '91',
                total: '100')]).coverage('gui') == new CoverageEvidence(PASS, 91.0d, 60.0d, 91L, 100L)
    }

    def "several coverage rows of other modules give no coverage"() {
        expect:
        new RunPoints([row('code_coverage', module: 'batch', line_pct: '91', met: '1'),
                       row('code_coverage', module: 'backend-api', line_pct: '50', met: '0')]).coverage('gui') ==
                new CoverageEvidence(NO_DATA, null, null, null, null)
    }

    def "a run without tests reports every suite without data in stage order"() {
        expect:
        new RunPoints([]).testSuites('gui') == [
                new TestSuiteEvidence(TestStage.SMOKE, NO_DATA, null, null, null, null, null),
                new TestSuiteEvidence(TestStage.REGRESSION, NO_DATA, null, null, null, null, null),
                new TestSuiteEvidence(TestStage.PERFORMANCE, NO_DATA, null, null, null, null, null)]
    }

    def "each suite is read from the module's test jobs and the stage that ran them"() {
        given:
        def points = new RunPoints([
                row('test_execution', module: 'gui', suite: 'Smoke tests', total: '5', passed: '4', failed: '0',
                        not_configured: '1', duration_ms: '12345.0'),
                row('test_execution', module: 'gui', suite: 'Regression tests (>60% user stories coverage)', total: '10',
                        passed: '8', failed: '2', not_configured: '0', duration_ms: '600000'),
                row('test_execution', module: 'backend-api', suite: 'Smoke tests', total: '3', passed: '0', failed: '3'),
                row('stage_event', stage: 'Performance tests', status: 'SKIP', order: '9')])

        expect:
        points.testSuites('gui') == [
                new TestSuiteEvidence(TestStage.SMOKE, PASS, 5L, 4L, 0L, 1L, 12345L),
                new TestSuiteEvidence(TestStage.REGRESSION, WARN, 10L, 8L, 2L, 0L, 600000L),
                new TestSuiteEvidence(TestStage.PERFORMANCE, SKIP, null, null, null, null, null)]
    }

    def "the status of the stage that ran the tests wins over the test counts"() {
        given:
        def points = new RunPoints([
                row('test_execution', module: 'gui', suite: 'Smoke tests', total: '2', passed: '2', failed: '0'),
                row('stage_event', stage: 'Smoke tests', status: 'FAIL', order: '5'),
                row('test_execution', module: 'gui', suite: 'Performance tests', total: '1', passed: '0', failed: '1'),
                row('stage_event', stage: 'PERFORMANCE TESTS', status: 'pass', order: '7')])

        when:
        def suites = points.testSuites('gui')

        then:
        suites*.status == [FAIL, NO_DATA, PASS]
        suites[0].passed() == 2L
        suites[2].failed() == 1L
    }

    def "a suite without a failure count passes"() {
        expect:
        new RunPoints([row('test_execution', module: 'gui', suite: 'smoke', total: '1', passed: '1', failed: failedCount)])
                .testSuites('gui')[0].with { [status(), failed()] } == [PASS, null]

        where:
        failedCount << [null, '', 'none']
    }

    def "test jobs of other modules, or without a suite, are not read"() {
        given:
        def points = new RunPoints([
                row('test_execution', module: 'backend-api', suite: 'Smoke tests', total: '3', failed: '0'),
                row('test_execution', module: 'batch', suite: 'Smoke tests', total: '4', failed: '0'),
                row('test_execution', module: 'gui', total: '9', failed: '0')])

        expect:
        points.testSuites('gui')*.jobs() == [null, null, null]
    }

    def "the only test job row of a suite is read whatever its module"() {
        expect:
        new RunPoints([row('test_execution', module: 'cert-gui', suite: 'Regression tests', total: '7', passed: '7',
                failed: '0')]).testSuites('gui')[1] ==
                new TestSuiteEvidence(TestStage.REGRESSION, PASS, 7L, 7L, 0L, null, null)
    }

    def "a run without scans reports each scanner without data, with its link"() {
        expect:
        new RunPoints([]).scans('gui', links) == [
                new ScanEvidence(SAST, NO_DATA, null, null, null, null, null, null, null, APPSCAN),
                new ScanEvidence(DAST, NO_DATA, null, null, null, null, null, null, null, APPSCAN),
                new ScanEvidence(SONARQUBE, NO_DATA, null, null, null, null, null, null, null, SONAR),
                new ScanEvidence(NEXUS_IQ, NO_DATA, null, null, null, null, null, null, null, NEXUS_IQ_URL)]
    }

    def "findings are read against the limits, Nexus IQ and SonarQube counts from the vulnerabilities"() {
        given:
        def points = new RunPoints([
                row('security_findings', module: 'gui', scanner: 'sast', status: 'WARN', critical: '0', high: '2',
                        medium: '5', low: '7', max_critical: '0', max_high: '0', max_medium: '10'),
                row('security_findings', module: 'gui', scanner: 'dast', status: 'PASS', critical: '0', high: '0',
                        medium: '1', low: '3', max_critical: '0', max_high: '1', max_medium: '5'),
                row('security_findings', module: 'gui', scanner: 'niq', status: 'FAIL', critical: '0', high: '0',
                        medium: '0', low: '0', max_critical: '0', max_high: '2', max_medium: '8'),
                row('vulnerabilities', scanner: 'nexusiq', critical: '1', high: '3', medium: '4', low: '6'),
                row('vulnerabilities', scanner: 'sonar', critical: '0', high: '1', medium: '12', low: '40'),
                row('policy_status', scanner: 'sast', status: 'BLOCKED'),
                row('policy_status', scanner: 'sonar', status: 'PASS'),
                row('policy_status', scanner: 'iast', status: 'FAIL')])

        expect:
        points.scans('gui', links) == [
                new ScanEvidence(SAST, BLOCKED, 0L, 2L, 5L, 7L, 0L, 0L, 10L, APPSCAN),
                new ScanEvidence(DAST, PASS, 0L, 0L, 1L, 3L, 0L, 1L, 5L, APPSCAN),
                new ScanEvidence(SONARQUBE, PASS, 0L, 1L, 12L, 40L, null, null, null, SONAR),
                new ScanEvidence(NEXUS_IQ, FAIL, 1L, 3L, 4L, 6L, 0L, 2L, 8L, NEXUS_IQ_URL)]
    }

    def "a scanner the policy did not require has no findings"() {
        given:
        def points = new RunPoints([row('policy_status', scanner: 'dast', status: 'NOT_REQUIRED'),
                                    row('policy_status', scanner: 'iast', status: 'SKIP')])

        when:
        def scans = points.scans('gui', links)

        then:
        scans[1] == new ScanEvidence(DAST, NOT_REQUIRED, null, null, null, null, null, null, null, APPSCAN)
        scans[3] == new ScanEvidence(NEXUS_IQ, SKIP, null, null, null, null, null, null, null, NEXUS_IQ_URL)
    }

    def "findings of the module are chosen among several, and a single row of another name is taken"() {
        given:
        def several = new RunPoints([
                row('security_findings', module: 'backend-api', scanner: 'sast', status: 'FAIL', critical: '4'),
                row('security_findings', module: 'gui', scanner: 'sast', status: 'PASS', critical: '0'),
                row('security_findings', module: 'batch', scanner: 'dast', status: 'FAIL', critical: '1'),
                row('security_findings', module: 'reports', scanner: 'dast', status: 'FAIL', critical: '2')])
        def single = new RunPoints([
                row('security_findings', module: 'cert-gui', scanner: 'sast', status: 'warn', critical: '1')])

        expect:
        several.scans('gui', links)[0].with { [status(), critical()] } == [PASS, 0L]
        several.scans('gui', links)[1].with { [status(), critical()] } == [NO_DATA, null]
        single.scans('gui', links)[0].with { [status(), critical()] } == [WARN, 1L]
    }

    def "without links the scans link nowhere"() {
        given:
        def none = EvidenceLinks.of(null, null, null, null, null, null, null)

        expect:
        new RunPoints([]).scans('gui', none)*.link() == [null, null, null, null]
    }

    def "a run without a release gate decision has none"() {
        expect:
        new RunPoints([row('stage_event', stage: 'Build')]).releaseGate() == null
    }

    def "the release gate tag allowed #allowed with violations #violations and reason #reason reads as #evidence"() {
        expect:
        new RunPoints([row('release_gate', [allowed: allowed, violations: violations, reason: reason]
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
        def points = new RunPoints([
                row('stage_event', stage: 'SAST', status: 'WARN', order: '3', duration_s: '300',
                        reason: 'High findings above the limit'),
                row('stage_event', stage: 'Cleanup', status: 'something', duration_s: ''),
                row('stage_event', stage: 'Build', status: 'PASS', order: '1', duration_s: '65.9'),
                row('stage_event', stage: 'Unit tests', status: 'pass', order: '2.0', duration_s: '120', reason: ' '),
                row('policy_status', scanner: 'sast', status: 'WARN')])

        expect:
        points.stages() == [
                new StageEvidence('Build', PASS, 65L, null),
                new StageEvidence('Unit tests', PASS, 120L, null),
                new StageEvidence('SAST', WARN, 300L, 'High findings above the limit'),
                new StageEvidence('Cleanup', NO_DATA, null, null)]
    }

    def "a run without stage events has no stages"() {
        expect:
        new RunPoints([row('release_gate', allowed: 'yes')]).stages() == []
    }

    def "the value #value reads as the number #number and the decimal #decimal"() {
        expect:
        RunPoints.number(value) == number
        RunPoints.decimal(value) == decimal

        where:
        value   || number | decimal
        null    || null   | null
        ''      || null   | null
        '   '   || null   | null
        'abc'   || null   | null
        '12'    || 12L    | 12.0d
        ' 7 '   || 7L     | 7.0d
        '12.9'  || 12L    | 12.9d
        '-1.5'  || -2L    | -1.5d
        '1e3'   || 1000L  | 1000.0d
    }

    static Map<String, String> row(Map values, String measurement) {
        row(measurement, values)
    }

    static Map<String, String> row(String measurement, Map values) {
        ([_measurement: measurement] + values).collectEntries { key, value -> [key, value as String] } as Map<String, String>
    }
}
