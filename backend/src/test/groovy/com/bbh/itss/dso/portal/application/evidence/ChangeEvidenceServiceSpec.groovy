package com.bbh.itss.dso.portal.application.evidence

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort
import com.bbh.itss.dso.portal.application.monitoring.MonitoringTargetsService
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.evidence.BuildEvidence
import com.bbh.itss.dso.portal.domain.evidence.CheckStatus
import com.bbh.itss.dso.portal.domain.evidence.CoverageEvidence
import com.bbh.itss.dso.portal.domain.evidence.ReleaseGateEvidence
import com.bbh.itss.dso.portal.domain.evidence.RunEvidence
import com.bbh.itss.dso.portal.domain.evidence.StageEvidence
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.monitoring.MetricsUnavailableException
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.evidence.RunEvidenceSpec.point
import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class ChangeEvidenceServiceSpec extends Specification {

    static final Instant FINISHED = Instant.parse('2026-10-04T10:00:00Z')
    static final String GUI_JOB = 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
    static final String APPSCAN = "https://bbh.cloud.appscan.com/main/myapps/$APP_ID/scans"
    static final String NOT_CONFIGURED = 'InfluxDB is not configured for the portal'

    ProductRepositoryPort products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineRepositoryPort pipelines = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineRunsPort runs = Mock()
    RunEvidencePort evidence = Mock()
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    def targets = new MonitoringTargetsService(products, pipelines, settings)

    def evidenceService = new ChangeEvidenceService(targets, runs, evidence)

    Product certScanner = product(id: 1L, code: 'CERT', name: 'CertScanner', services: [
            [name: 'gui', id: 10L,
             sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonarqube'])),
             nexusIq: NexusIqSettings.of('cert-gui', ['**/build/libs/*.war'])],
            [name: 'backend-api', id: 11L],
            [name: 'batch', id: 12L]])
    Service gui = certScanner.services()[0]
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
    def guiEvidence = new RunEvidence([
            point('code_coverage', module: 'gui', measured: 'yes', line_pct: '82.5', required: '60', covered: '825',
                    total: '1000', met: '1'),
            point('test_execution', module: 'gui', suite: 'Smoke tests', total: '3', passed: '3', failed: '0',
                    not_configured: '0', duration_ms: '4500'),
            point('security_findings', module: 'gui', scanner: 'sast', status: 'WARN', critical: '0', high: '2',
                    medium: '5', low: '1', max_critical: '0', max_high: '0', max_medium: '10'),
            point('vulnerabilities', scanner: 'sonar', critical: '0', high: '0', medium: '3', low: '8'),
            point('policy_status', scanner: 'sonar', status: 'PASS'),
            point('release_gate', allowed: 'yes', violations: '0', reason: ''),
            point('stage_event', stage: 'Build', status: 'PASS', order: '1', duration_s: '120')])

    def setup() {
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [guiFull, guiSast, apiFull]
    }

    def "every pipeline of the product comes with what its latest run proved"() {
        when:
        def result = evidenceService.product(1L)

        then:
        1 * runs.latestRuns([guiTag, guiSastTag, apiTag] as Set) >> [(guiTag): guiRun, (apiTag): apiRun]
        1 * evidence.evidenceOf({ it == [(guiTag): guiRun, (apiTag): apiRun] }) >> [(guiTag): guiEvidence]
        result.product() == certScanner
        result.metricsError() == null
        result.services()*.service() == certScanner.services()
        result.services().collect { it.pipelines()*.pipeline() } == [[guiFull, guiSast], [apiFull], []]
        def guiFullEvidence = result.services()[0].pipelines()[0]
        guiFullEvidence.jenkinsJobUrl() == GUI_JOB
        guiFullEvidence.status() == RunResult.SUCCESS
        with(guiFullEvidence.run()) {
            build() == new BuildEvidence(42L, FINISHED, RunResult.SUCCESS, 'develop', 'a1b2c3d', 900L,
                    'DevSecOps/CERT/gui-full', GUI_JOB + '42/', GUI_JOB + '42/Pipeline_20Report/',
                    GUI_JOB + '42/testReport/', GUI_JOB + '42/artifact/')
            coverage() == new CoverageEvidence(CheckStatus.PASS, 82.5d, 60.0d, 825L, 1000L)
            scans()*.status() == [CheckStatus.WARN, CheckStatus.NO_DATA, CheckStatus.PASS, CheckStatus.NO_DATA]
            scans()*.link() == [APPSCAN, APPSCAN, 'https://tools.bbh.com/sonar/dashboard?id=cert-gui',
                                'https://tools.bbh.com/IQ/']
            releaseGate() == new ReleaseGateEvidence(true, 0L, null)
            stages() == [new StageEvidence('Build', CheckStatus.PASS, 120L, null)]
        }
        def guiSastEvidence = result.services()[0].pipelines()[1]
        guiSastEvidence.jenkinsJobUrl() == null
        guiSastEvidence.status() == RunResult.DISABLED
        guiSastEvidence.run() == null
    }

    def "when the metrics cannot be read every pipeline is listed without a run and the reason is given"() {
        when:
        def result = evidenceService.product(1L)

        then:
        1 * runs.latestRuns(_) >> { throw new MetricsUnavailableException(NOT_CONFIGURED) }
        0 * evidence.evidenceOf(_)
        result.metricsError() == NOT_CONFIGURED
        result.services().collect { it.pipelines()*.pipeline()*.id() } == [[100L, 101L], [102L], []]
        result.services()*.pipelines().flatten()*.status() == [RunResult.NO_DATA, RunResult.DISABLED,
                                                               RunResult.NO_DATA]
        result.services()*.pipelines().flatten()*.run() == [null, null, null]
        result.services()[0].pipelines()[0].jenkinsJobUrl() == GUI_JOB
    }

    def "a product without pipelines asks for no runs of any pipeline"() {
        given:
        products.load(2L) >> Optional.of(product(id: 2L, code: 'PAY', name: 'Payments Hub',
                services: [[name: 'gateway', id: 20L]]))

        when:
        def result = evidenceService.product(2L)

        then:
        1 * runs.latestRuns([] as Set) >> [:]
        1 * evidence.evidenceOf({ it.isEmpty() }) >> [:]
        result.metricsError() == null
        result.services()*.service()*.name() == ['gateway']
        result.services()[0].pipelines() == []
    }

    def "a failure reading the evidence keeps the runs already read and reports the reason"() {
        given:
        runs.latestRuns(_) >> [(guiTag): guiRun]

        when:
        def result = evidenceService.product(1L)

        then:
        1 * evidence.evidenceOf(_) >> {
            throw new MetricsUnavailableException("InfluxDB could not be read: Text 'yesterday' could not be parsed")
        }
        result.metricsError() == "InfluxDB could not be read: Text 'yesterday' could not be parsed"
        result.services()[0].pipelines()[0].status() == RunResult.SUCCESS
        with(result.services()[0].pipelines()[0].run()) {
            build().number() == 42L
            build().url() == GUI_JOB + '42/'
            coverage().status() == CheckStatus.NO_DATA
            stages() == []
        }
    }

    def "a pipeline of a service the product no longer has is an inconsistency, and an unknown product is not found"() {
        given:
        pipelines.findByProductId(3L) >> [pipeline(id: 300L, productId: 3L, serviceId: 30L)]
        products.load(3L) >> Optional.of(product(id: 3L, code: 'PAY', services: [[name: 'gateway', id: 31L]]))

        when:
        evidenceService.product(3L)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'pipeline 300 belongs to no service of product 3'

        when:
        evidenceService.product(9L)

        then:
        def missing = thrown(NotFoundException)
        missing.message == 'Product 9 does not exist'
        0 * runs._
    }

    def "a pipeline with job #pipelineJob whose run reported #runJob under Jenkins #jenkinsUrl links #jobUrl and build #buildUrl"() {
        given:
        def linked = pipeline(id: 100L, serviceId: 10L, jenkinsJob: pipelineJob)
        def run = new PipelineRun(FINISHED, RunResult.SUCCESS, 'main', 42L, 60L, null, runJob, null, null, null, null,
                null, null)
        def platform = GlobalSettingsValues.bbhDefaults().platform().withJenkinsUrl(jenkinsUrl)

        when:
        def found = ChangeEvidenceService.pipeline(gui, linked, run, null, platform)

        then:
        found.jenkinsJobUrl() == jobUrl
        found.run().build().url() == buildUrl
        found.run().build().job() == runJob

        where:
        pipelineJob                        | runJob                             | jenkinsUrl             || jobUrl                             | buildUrl
        'DevSecOps/CERT/gui-full'          | 'DevSecOps/CERT/app/develop'       | 'https://jenkins.test' || GUI_JOB                            | 'https://jenkins.test/job/DevSecOps/job/CERT/job/app/job/develop/42/'
        null                               | 'DevSecOps/CERT/gui-full'          | 'https://jenkins.test' || null                               | GUI_JOB + '42/'
        null                               | 'https://jenkins.bbh.com/job/gui/' | null                   || null                               | 'https://jenkins.bbh.com/job/gui/42/'
        'https://jenkins.bbh.com/job/own/' | null                               | null                   || 'https://jenkins.bbh.com/job/own/' | 'https://jenkins.bbh.com/job/own/42/'
        'DevSecOps/CERT/gui-full'          | null                               | 'https://jenkins.test' || GUI_JOB                            | GUI_JOB + '42/'
        null                               | 'DevSecOps/CERT/gui-full'          | null                   || null                               | null
        'https://jenkins.bbh.com/job/own/' | 'DevSecOps/CERT/app/develop'       | null                   || 'https://jenkins.bbh.com/job/own/' | 'https://jenkins.bbh.com/job/own/42/'
        null                               | null                               | 'https://jenkins.test' || null                               | null
    }

    def "a run without a build number links no build"() {
        given:
        def run = new PipelineRun(FINISHED, RunResult.FAILURE, null, null, null, null, null, null, null, null, null,
                null, null)
        def platform = GlobalSettingsValues.bbhDefaults().platform().withJenkinsUrl('https://jenkins.test')

        when:
        def found = ChangeEvidenceService.pipeline(gui, guiFull, run, RunEvidence.none(), platform)

        then:
        found.status() == RunResult.FAILURE
        found.jenkinsJobUrl() == GUI_JOB
        found.run().build() == new BuildEvidence(null, FINISHED, RunResult.FAILURE, null, null, null, null, null, null,
                null, null)
    }
}
