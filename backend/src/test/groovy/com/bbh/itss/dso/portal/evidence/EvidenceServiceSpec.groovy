package com.bbh.itss.dso.portal.evidence

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.BuildEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.CoverageEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.PipelineEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ReleaseGateEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ScanEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.StageEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.TestSuiteEvidence
import com.bbh.itss.dso.portal.monitoring.MetricsTag
import com.bbh.itss.dso.portal.monitoring.PipelineMetricsRepository
import com.bbh.itss.dso.portal.monitoring.PipelineRun
import com.bbh.itss.dso.portal.monitoring.RunResult
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.dso.portal.evidence.RunPointsSpec.row
import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class EvidenceServiceSpec extends Specification {

    static final Instant FINISHED = Instant.parse('2026-10-04T10:00:00Z')
    static final String GUI_JOB = 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
    static final String APPSCAN = "https://bbh.cloud.appscan.com/main/myapps/$APP_ID/scans"

    ProductRepositoryPort products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineRepositoryPort pipelines = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineMetricsRepository runs = Mock()
    RunEvidenceRepository evidence = Mock()
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    @Subject
    def evidenceService = new EvidenceService(products, pipelines, runs, evidence, settings)

    Product certScanner = product(id: 1L, code: 'CERT', name: 'CertScanner', description: 'Scans certificates',
            ownerTeam: 'TA', contactEmail: 'ta@bbh.com', services: [
            [name: 'gui', id: 10L, description: 'Angular GUI',
             sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonarqube'])),
             nexusIq: NexusIqSettings.of('cert-gui', ['**/build/libs/*.war']),
             scm: ScmSettings.of('https://bitbucket.bbh.com/scm/cert/gui.git', 'bitbucket-token'),
             deployment: deployment(artifactName: 'cert-gui.war')],
            [name: 'backend-api', id: 11L],
            [name: 'batch', id: 12L]])
    Pipeline guiFull = pipeline(id: 100L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full')
    Pipeline guiSast = pipeline(id: 101L, serviceId: 10L, type: PipelineType.SAST, keys: [revokedKey(reason: 'retired')])
    Pipeline apiFull = pipeline(id: 102L, serviceId: 11L)

    def guiTag = new MetricsTag('CERT-gui', 'test')
    def guiSastTag = new MetricsTag('CERT-guisast', 'test')
    def apiTag = new MetricsTag('CERT-backend-api', 'test')

    def guiRun = new PipelineRun(FINISHED, RunResult.SUCCESS, 'develop', 42L, 900L, 'a1b2c3d', 'DevSecOps/CERT/gui-full',
            12L, 11L, 1L, 0L, 0L, 0L)
    def apiRun = new PipelineRun(FINISHED.minusSeconds(3600), RunResult.UNSTABLE, 'feature/login', 7L, 300L, 'e4f5a6b',
            'DevSecOps/CERT/api-full', 10L, 9L, 1L, 0L, 0L, 0L)
    def guiPoints = new RunPoints([
            row('code_coverage', module: 'gui', measured: 'yes', line_pct: '82.5', required: '60', covered: '825',
                    total: '1000', met: '1'),
            row('test_execution', module: 'gui', suite: 'Smoke tests', total: '3', passed: '3', failed: '0',
                    not_configured: '0', duration_ms: '4500'),
            row('security_findings', module: 'gui', scanner: 'sast', status: 'WARN', critical: '0', high: '2',
                    medium: '5', low: '1', max_critical: '0', max_high: '0', max_medium: '10'),
            row('vulnerabilities', scanner: 'sonar', critical: '0', high: '0', medium: '3', low: '8'),
            row('policy_status', scanner: 'sonar', status: 'PASS'),
            row('release_gate', allowed: 'yes', violations: '0', reason: ''),
            row('stage_event', stage: 'Build', status: 'PASS', order: '1', duration_s: '120')])

    def setup() {
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [guiFull, guiSast, apiFull]
    }

    def "every pipeline of the product comes with what its latest run proved"() {
        when:
        def result = evidenceService.product(1L)

        then:
        1 * runs.configured() >> true
        1 * runs.latestRuns([guiTag, guiSastTag, apiTag] as Set) >> [(guiTag): guiRun, (apiTag): apiRun]
        1 * evidence.pointsOf({ it == [(guiTag): guiRun, (apiTag): apiRun] }) >> [(guiTag): guiPoints]
        with(result) {
            productId == 1
            code == 'CERT'
            name == 'CertScanner'
            description == 'Scans certificates'
            ownerTeam == 'TA'
            contactEmail == 'ta@bbh.com'
            metricsError == null
            services*.name == ['gui', 'backend-api', 'batch']
        }
        with(result.services[0]) {
            serviceId == 10
            description == 'Angular GUI'
            repositoryUrl == 'https://bitbucket.bbh.com/scm/cert/gui.git'
            artifactName == 'cert-gui.war'
            appScanApplicationId == APP_ID
            sonarProjectKey == 'cert-gui'
            nexusIqApplication == 'cert-gui'
            pipelines*.pipelineId == [100L, 101L]
        }
        with(result.services[0].pipelines[0]) {
            type == PipelineType.FULL
            enabled
            jenkinsJobUrl == GUI_JOB
            status == RunResult.SUCCESS
            run.build == new BuildEvidence(42L, FINISHED, RunResult.SUCCESS, 'develop', 'a1b2c3d', 900L,
                    'DevSecOps/CERT/gui-full', GUI_JOB + '42/', GUI_JOB + '42/Pipeline_20Report/',
                    GUI_JOB + '42/testReport/', GUI_JOB + '42/artifact/')
            run.coverage == new CoverageEvidence(CheckStatus.PASS, 82.5d, 60.0d, 825L, 1000L)
            run.testSuites == [new TestSuiteEvidence(TestStage.SMOKE, CheckStatus.PASS, 3L, 3L, 0L, 0L, 4500L),
                               new TestSuiteEvidence(TestStage.REGRESSION, CheckStatus.NO_DATA, null, null, null, null, null),
                               new TestSuiteEvidence(TestStage.PERFORMANCE, CheckStatus.NO_DATA, null, null, null, null, null)]
            run.scans == [
                    new ScanEvidence(EvidenceScanner.SAST, CheckStatus.WARN, 0L, 2L, 5L, 1L, 0L, 0L, 10L, APPSCAN),
                    new ScanEvidence(EvidenceScanner.DAST, CheckStatus.NO_DATA, null, null, null, null, null, null, null, APPSCAN),
                    new ScanEvidence(EvidenceScanner.SONARQUBE, CheckStatus.PASS, 0L, 0L, 3L, 8L, null, null, null,
                            'https://tools.bbh.com/sonar/dashboard?id=cert-gui'),
                    new ScanEvidence(EvidenceScanner.NEXUS_IQ, CheckStatus.NO_DATA, null, null, null, null, null, null, null,
                            'https://tools.bbh.com/IQ/')]
            run.releaseGate == new ReleaseGateEvidence(true, 0L, null)
            run.stages == [new StageEvidence('Build', CheckStatus.PASS, 120L, null)]
        }
        result.services[0].pipelines[1] == new PipelineEvidence(101L, PipelineType.SAST, false, null, RunResult.DISABLED, null)
        result.services[2].pipelines == []
    }

    def "a pipeline without a job of its own links the build of the job its run reported"() {
        given:
        runs.configured() >> true
        runs.latestRuns(_) >> [(apiTag): apiRun]
        evidence.pointsOf(_) >> [:]

        when:
        def api = evidenceService.product(1L).services[1]

        then:
        api.sonarProjectKey == null
        api.nexusIqApplication == null
        api.repositoryUrl == null
        with(api.pipelines[0]) {
            jenkinsJobUrl == null
            status == RunResult.UNSTABLE
            run.build.job == 'DevSecOps/CERT/api-full'
            run.build.url == 'https://jenkins.test/job/DevSecOps/job/CERT/job/api-full/7/'
            run.build.reportUrl == 'https://jenkins.test/job/DevSecOps/job/CERT/job/api-full/7/Pipeline_20Report/'
            run.coverage == new CoverageEvidence(CheckStatus.NO_DATA, null, null, null, null)
            run.testSuites*.status == [CheckStatus.NO_DATA] * 3
            run.scans*.status == [CheckStatus.NO_DATA] * 4
            run.scans*.link == [APPSCAN, APPSCAN, null, 'https://tools.bbh.com/IQ/']
            run.releaseGate == null
            run.stages == []
        }
        with(evidenceService.product(1L).services[0].pipelines[0]) {
            status == RunResult.NO_DATA
            run == null
            jenkinsJobUrl == GUI_JOB
        }
    }

    def "without InfluxDB every pipeline is listed without a run and the reason is given"() {
        when:
        def result = evidenceService.product(1L)

        then:
        1 * runs.configured() >> false
        0 * runs.latestRuns(_)
        0 * evidence.pointsOf(_)
        result.metricsError == 'InfluxDB is not configured for the portal'
        result.metricsError == EvidenceService.NOT_CONFIGURED
        result.services*.pipelines*.pipelineId == [[100L, 101L], [102L], []]
        result.services*.pipelines.flatten()*.status == [RunResult.NO_DATA, RunResult.DISABLED, RunResult.NO_DATA]
        result.services*.pipelines.flatten()*.run == [null, null, null]
        result.services[0].pipelines[0].jenkinsJobUrl == GUI_JOB
    }

    def "a product without pipelines needs no query"() {
        given:
        products.load(2L) >> Optional.of(product(id: 2L, code: 'PAY', name: 'Payments Hub',
                services: [[name: 'gateway', id: 20L]]))

        when:
        def result = evidenceService.product(2L)

        then:
        1 * runs.configured() >> true
        0 * runs.latestRuns(_)
        0 * evidence.pointsOf(_)
        result.metricsError == null
        result.services*.name == ['gateway']
        result.services[0].pipelines == []
    }

    def "when InfluxDB cannot be read the reason is returned, cut to 300 characters"() {
        given:
        runs.configured() >> true
        runs.latestRuns(_) >> { throw failure }

        when:
        def result = evidenceService.product(1L)

        then:
        0 * evidence.pointsOf(_)
        result.metricsError == error
        result.services*.pipelines.flatten()*.run == [null, null, null]
        result.services*.pipelines.flatten()*.status == [RunResult.NO_DATA, RunResult.DISABLED, RunResult.NO_DATA]

        where:
        failure                                             || error
        new IllegalStateException('401 Unauthorized')       || 'InfluxDB could not be read: 401 Unauthorized'
        new IllegalStateException('x' * 300)                || 'InfluxDB could not be read: ' + 'x' * 300
        new IllegalStateException('0123456789' * 40)        || 'InfluxDB could not be read: ' + ('0123456789' * 30)
        new RuntimeException()                              || 'InfluxDB could not be read: RuntimeException'
    }

    def "a failure reading the run points drops the runs already read"() {
        given:
        runs.configured() >> true
        runs.latestRuns(_) >> [(guiTag): guiRun]

        when:
        def result = evidenceService.product(1L)

        then:
        1 * evidence.pointsOf(_) >> { throw new IllegalArgumentException('Text \'yesterday\' could not be parsed') }
        result.metricsError == "InfluxDB could not be read: Text 'yesterday' could not be parsed"
        result.services[0].pipelines[0].run == null
        result.services[0].pipelines[0].status == RunResult.NO_DATA
    }

    def "an unknown product is not found"() {
        when:
        evidenceService.product(9L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Product 9 does not exist'
        0 * runs._
    }

    def "a pipeline with job #pipelineJob whose run reported #runJob under Jenkins #jenkinsUrl links #jobUrl and build #buildUrl"() {
        given:
        def linked = view(pipeline(id: 100L, serviceId: 10L, jenkinsJob: pipelineJob))
        def run = new PipelineRun(FINISHED, RunResult.SUCCESS, 'main', 42L, 60L, null, runJob, null, null, null, null, null,
                null)
        def platform = GlobalSettingsValues.bbhDefaults().platform().withJenkinsUrl(jenkinsUrl)

        when:
        def found = EvidenceService.pipeline(linked, run, null, platform)

        then:
        found.jenkinsJobUrl() == jobUrl
        found.run().build().url() == buildUrl
        found.run().build().job() == runJob

        where:
        pipelineJob                        | runJob                             | jenkinsUrl             || jobUrl                                    | buildUrl
        'DevSecOps/CERT/gui-full'          | 'DevSecOps/CERT/other'             | 'https://jenkins.test' || GUI_JOB                                   | GUI_JOB + '42/'
        null                               | 'DevSecOps/CERT/gui-full'          | 'https://jenkins.test' || null                                      | GUI_JOB + '42/'
        null                               | 'https://jenkins.bbh.com/job/gui/' | null                   || null                                      | 'https://jenkins.bbh.com/job/gui/42/'
        'https://jenkins.bbh.com/job/own/' | 'DevSecOps/CERT/gui-full'          | null                   || 'https://jenkins.bbh.com/job/own/'        | 'https://jenkins.bbh.com/job/own/42/'
        null                               | 'DevSecOps/CERT/gui-full'          | null                   || null                                      | null
        null                               | null                               | 'https://jenkins.test' || null                                      | null
    }

    def "a run without a build number links no build"() {
        given:
        def run = new PipelineRun(FINISHED, RunResult.FAILURE, null, null, null, null, null, null, null, null, null, null, null)
        def platform = GlobalSettingsValues.bbhDefaults().platform().withJenkinsUrl('https://jenkins.test')

        when:
        def found = EvidenceService.pipeline(view(guiFull), run, new RunPoints([]), platform)

        then:
        found.status() == RunResult.FAILURE
        found.jenkinsJobUrl() == GUI_JOB
        found.run().build() == new BuildEvidence(null, FINISHED, RunResult.FAILURE, null, null, null, null, null, null,
                null, null)
    }

    private PipelineView view(Pipeline pipeline) {
        PipelineView.of(certScanner, pipeline, 'https://jenkins.test')
    }
}
