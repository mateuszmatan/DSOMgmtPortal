package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static java.time.Duration.ofHours
import static java.time.Duration.ofSeconds

class EvidenceRegressionSpec extends PortalSpecification {

    static final String JOB = 'https://jenkins.bbh.com/job/DevSecOps/job/gui-full/'

    String code
    Map evidenced
    Map guiFull
    Map guiSast
    Instant finished

    def setup() {
        influx.reset()
        code = uniqueCode('EVI')
        evidenced = createProduct(product(code: code, name: "Evidenced $code", ownerTeam: 'Treasury Apps',
                contactEmail: 'treasury@bbh.com', services: [
                service(name: 'gui', description: 'Treasury web client', deployment: [target: 'VM', artifactName: 'gui.war'],
                        sonar: [projectKey: "$code-gui".toString(), command: [tasks: ['sonarqube']]],
                        nexusIqApplications: [[application: "$code-gui".toString(), scanPatterns: ['**/build/libs/*.war']]],
                        scm: [repositoryUrl: 'https://bitbucket.bbh.com/projects/TRE/repos/gui',
                              credentialsId: 'bitbucket-http-credentials']),
                service(name: 'batch')]))
        guiFull = pipelineFor(evidenced.services[0].id as long, pipeline(jenkinsJob: JOB))
        guiSast = pipelineFor(evidenced.services[0].id as long, pipeline(type: 'SAST'))
        api.post("/api/pipelines/$guiSast.id/keys/revoke", [reason: 'SAST runs inside the full pipeline'])

        finished = Instant.now().minus(ofHours(1))
        String project = "$code-gui"
        influx.addRun(project: project, time: finished, result: 'UNSTABLE', build: 42, durationSeconds: 900)
        def point = { Map args -> influx.addPoint([project: project, time: finished] + args) }
        point(measurement: 'code_coverage', module: 'gui', line_pct: '87.5', required: '80', covered: '875',
                total: '1000', met: '1', measured: 'yes')
        point(measurement: 'test_execution', module: 'gui', suite: 'unit', total: '240', passed: '236', failed: '0',
                skipped: '4', not_configured: '0', duration_ms: '81000', success_rate: '98.33')
        point(measurement: 'test_execution', module: 'gui', suite: 'smoke', total: '12', passed: '12', failed: '0',
                not_configured: '0', duration_ms: '5400')
        point(measurement: 'test_execution', module: 'gui', suite: 'regression-api', total: '40', passed: '38',
                failed: '2', not_configured: '0', duration_ms: '61000')
        point(measurement: 'security_findings', module: 'gui', scanner: 'sast', critical: '0', high: '1', medium: '3',
                low: '7', max_critical: '0', max_high: '2', max_medium: '10', status: 'pass')
        point(measurement: 'security_findings', module: 'gui', scanner: 'niq', critical: '0', high: '0', medium: '0',
                low: '0', max_critical: '0', max_high: '0', max_medium: '5', status: 'pass')
        point(measurement: 'policy_status', scanner: 'sast', status: 'pass')
        point(measurement: 'policy_status', scanner: 'iast', status: 'fail')
        point(measurement: 'policy_status', scanner: 'coverage', status: 'pass')
        point(measurement: 'vulnerabilities', scanner: 'sonar', critical: '0', high: '0', medium: '4', low: '12')
        point(measurement: 'vulnerabilities', scanner: 'nexusiq', critical: '0', high: '1', medium: '2', low: '0')
        point(measurement: 'build_evidence', module: 'gui', artifact_version: '2.3.0-42', sonar_quality_gate: 'WARN',
                sast_report_url: "${JOB}42/artifact/appscan/sast-report.html".toString(),
                config_rendered_at: '2026-10-05T08:15:00.000Z', config_sha256: '3b7e1f0a9c2d4e5f')
        point(measurement: 'release_gate', allowed: 'no', violations: '1', reason: 'Nexus IQ: 1 high finding, limit 0')
        point(measurement: 'stage_event', stage: 'Build', status: 'pass', order: '1', duration_s: '120',
                time: finished - ofSeconds(780))
        point(measurement: 'stage_event', stage: 'Smoke Tests', status: 'pass', order: '2', duration_s: '40',
                time: finished - ofSeconds(400))
        point(measurement: 'stage_event', stage: 'Regression Tests', status: 'warn', order: '3', duration_s: '70',
                time: finished - ofSeconds(200), reason: '2 tests failed')
        point(measurement: 'security_findings', module: 'gui', scanner: 'dast', critical: '5', high: '5', medium: '5',
                low: '5', status: 'fail', time: finished - ofSeconds(300))
    }

    def cleanup() {
        influx.reset()
    }

    def "a product shows every service and pipeline with what its latest run recorded"() {
        when:
        def evidence = api.get("/api/evidence/products/$evidenced.id").json

        then:
        evidence.metricsError == null
        evidence.code == code
        evidence.ownerTeam == 'Treasury Apps'
        evidence.contactEmail == 'treasury@bbh.com'
        evidence.services*.name == ['gui', 'batch']
        evidence.services[1].pipelines*.type == ['FULL']
        evidence.services[1].pipelines*.status == ['NO_DATA']
        evidence.services[1].pipelines[0].run == null

        def gui = "$code-gui"
        with(evidence.services[0]) {
            description == 'Treasury web client'
            repositoryUrl == 'https://bitbucket.bbh.com/projects/TRE/repos/gui'
            artifactName == 'gui.war'
            appScanApplicationId == APP_ID
            sonarProjectKey == gui
            nexusIqApplication == gui
            pipelines*.type == ['FULL', 'SAST']
            pipelines*.status == ['UNSTABLE', 'DISABLED']
            pipelines*.enabled == [true, false]
            pipelines[1].run == null
        }

        and: 'the build that produced the results, with the pages Jenkins keeps for it'
        def full = evidence.services[0].pipelines[0]
        full.jenkinsJobUrl == JOB
        Instant.parse(full.run.build.finishedAt as String) == finished
        full.run.build.subMap(['number', 'result', 'branch', 'commit', 'durationSeconds']) ==
                [number: 42, result: 'UNSTABLE', branch: 'develop', commit: 'a1b2c3d4e5f6', durationSeconds: 900]
        full.run.build.url == "${JOB}42/"
        full.run.build.reportUrl == "${JOB}42/Pipeline_20Report/"
        full.run.build.testReportUrl == "${JOB}42/testReport/"
        full.run.build.artifactsUrl == "${JOB}42/artifact/"

        and: 'the artifact it built and the portal configuration it ran with'
        full.run.build.artifactVersion == '2.3.0-42'
        full.run.build.configRenderedAt == '2026-10-05T08:15:00Z'
        full.run.build.configSha256 == '3b7e1f0a9c2d4e5f'

        and: 'unit test coverage and the unit, smoke, regression and performance suites'
        full.run.coverage == [status: 'PASS', linePercent: 87.5, requiredPercent: 80.0, coveredLines: 875, totalLines: 1000]
        full.run.testSuites == [
                [suite: 'UNIT', status: 'PASS', total: 240, passed: 236, failed: 0, skipped: 4, notConfigured: 0,
                 durationMs: 81000],
                [suite: 'SMOKE', status: 'PASS', total: 12, passed: 12, failed: 0, skipped: null, notConfigured: 0,
                 durationMs: 5400],
                [suite: 'REGRESSION', status: 'WARN', total: 40, passed: 38, failed: 2, skipped: null, notConfigured: 0,
                 durationMs: 61000],
                [suite: 'PERFORMANCE', status: 'NO_DATA', total: null, passed: null, failed: null, skipped: null,
                 notConfigured: null, durationMs: null]]

        and: 'SAST, DAST, SonarQube and Nexus IQ with the reports the build recorded, else the portal links'
        full.run.scans*.scanner == ['SAST', 'DAST', 'SONARQUBE', 'NEXUS_IQ']
        full.run.scans[0] == [scanner : 'SAST', status: 'PASS', critical: 0, high: 1, medium: 3, low: 7, maxCritical: 0,
                              maxHigh : 2, maxMedium: 10, qualityGate: null,
                              link    : "${JOB}42/artifact/appscan/sast-report.html"]
        full.run.scans[1].status == 'NO_DATA'
        full.run.scans[1].critical == null
        full.run.scans[1].link == "https://bbh.cloud.appscan.com/main/myapps/$APP_ID/scans"
        full.run.scans[2] == [scanner : 'SONARQUBE', status: 'WARN', critical: 0, high: 0, medium: 4, low: 12,
                              maxCritical: null, maxHigh: null, maxMedium: null, qualityGate: 'WARN',
                              link    : "https://tools.bbh.com/sonar/dashboard?id=$code-gui"]
        full.run.scans[3] == [scanner : 'NEXUS_IQ', status: 'FAIL', critical: 0, high: 1, medium: 2, low: 0,
                              maxCritical: 0, maxHigh: 0, maxMedium: 5, qualityGate: null,
                              link    : 'https://tools.bbh.com/IQ/']

        and: 'the release gate and every stage in the order it ran'
        full.run.releaseGate == [allowed: false, violations: 1, reason: 'Nexus IQ: 1 high finding, limit 0']
        full.run.stages*.name == ['Build', 'Smoke Tests', 'Regression Tests']
        full.run.stages[2] == [name: 'Regression Tests', status: 'WARN', durationSeconds: 70, reason: '2 tests failed']

        and: 'one query reads the evidence of all the product pipelines'
        influx.requests.count { it.body.query.contains('"security_findings"') } == 1
    }

    def "the build links follow the job that recorded the run, not the job the pipeline was given"() {
        given:
        def settings = api.get('/api/settings').json as Map
        assert api.put('/api/settings', settings + [platform: settings.platform +
                [jenkinsUrl: 'https://jenkins.bbh.com/']]).status == 200
        def branchJob = "DevSecOps/$code/gui/develop"
        influx.reset()
        influx.addRun(project: "$code-gui", time: finished, result: 'SUCCESS', build: 57, job: branchJob)

        when:
        def full = api.get("/api/evidence/products/$evidenced.id").json.services[0].pipelines[0]
        def monitored = api.get("/api/monitoring/pipelines/$guiFull.id").json

        then: 'the multibranch build of the branch job, not build 57 of the configured project'
        String build = "https://jenkins.bbh.com/job/DevSecOps/job/$code/job/gui/job/develop/57/"
        full.jenkinsJobUrl == JOB
        full.run.build.job == branchJob
        full.run.build.url == build
        full.run.build.reportUrl == build + 'Pipeline_20Report/'
        full.run.build.testReportUrl == build + 'testReport/'
        full.run.build.artifactsUrl == build + 'artifact/'
        monitored.lastRun.buildUrl == build
        monitored.recentRuns*.buildUrl == [build]

        cleanup:
        def current = api.get('/api/settings').json
        api.put('/api/settings', settings + [version: current.version])
    }

    def "a run that recorded nothing besides its result still links its build"() {
        given:
        influx.reset()
        influx.addRun(project: "$code-gui", time: finished, result: 'SUCCESS', build: 7)

        when:
        def full = api.get("/api/evidence/products/$evidenced.id").json.services[0].pipelines[0]

        then:
        full.status == 'SUCCESS'
        full.run.build.url == "${JOB}7/"
        full.run.coverage.status == 'NO_DATA'
        full.run.testSuites*.status == ['NO_DATA', 'NO_DATA', 'NO_DATA', 'NO_DATA']
        full.run.build.subMap(['artifactVersion', 'configRenderedAt', 'configSha256']) ==
                [artifactVersion: null, configRenderedAt: null, configSha256: null]
        full.run.scans*.status == ['NO_DATA', 'NO_DATA', 'NO_DATA', 'NO_DATA']
        full.run.releaseGate == null
        full.run.stages == []
    }

    def "when InfluxDB fails every pipeline is still listed, with the reason"() {
        given:
        influx.failWith(500)

        when:
        def evidence = api.get("/api/evidence/products/$evidenced.id").json

        then:
        evidence.metricsError.startsWith('InfluxDB could not be read')
        evidence.services[0].pipelines*.pipelineId == [guiFull.id, guiSast.id]
        evidence.services[0].pipelines*.run == [null, null]
        evidence.services[0].pipelines*.status == ['NO_DATA', 'DISABLED']
    }

    def "an unknown product is not found"() {
        expect:
        api.get('/api/evidence/products/999999').status == 404
    }
}
