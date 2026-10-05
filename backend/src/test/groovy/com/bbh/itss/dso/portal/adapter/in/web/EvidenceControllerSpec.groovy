package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.evidence.port.in.PipelineEvidence
import com.bbh.itss.dso.portal.application.evidence.port.in.ProductEvidence
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase
import com.bbh.itss.dso.portal.application.evidence.port.in.ServiceEvidence
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.domain.evidence.BuildEvidence
import com.bbh.itss.dso.portal.domain.evidence.CheckStatus
import com.bbh.itss.dso.portal.domain.evidence.CoverageEvidence
import com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner
import com.bbh.itss.dso.portal.domain.evidence.ReleaseGateEvidence
import com.bbh.itss.dso.portal.domain.evidence.RunEvidenceReport
import com.bbh.itss.dso.portal.domain.evidence.ScanEvidence
import com.bbh.itss.dso.portal.domain.evidence.StageEvidence
import com.bbh.itss.dso.portal.domain.evidence.TestSuiteEvidence
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class EvidenceControllerSpec extends Specification {

    static final String BUILD = 'https://jenkins.test/job/gui-full/42/'

    QueryEvidenceUseCase evidence = Mock()
    MockMvc mvc = WebMvc.of(new EvidenceController(evidence))

    Product certScanner = product(id: 1L, code: 'CERT', name: 'CertScanner', ownerTeam: 'TA',
            contactEmail: 'ta@bbh.com', services: [[name: 'gui', id: 10L, description: 'Angular GUI',
            sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonarqube'])),
            nexusIq: NexusIqSettings.of('cert-gui', ['**/build/libs/*.war']),
            scm: ScmSettings.of('https://bitbucket.bbh.com/scm/cert/gui.git', 'bitbucket-token'),
            deployment: deployment(artifactName: 'cert-gui.war')]])

    def "a product's evidence lists each service's pipelines with their latest run"() {
        given:
        String buildUrl = BUILD
        def run = new RunEvidenceReport(
                new BuildEvidence(42L, Instant.parse('2026-10-04T10:00:00Z'), RunResult.SUCCESS, 'develop', 'a1b2c3d',
                        900L, 'gui-full', BUILD, BUILD + 'Pipeline_20Report/', BUILD + 'testReport/',
                        BUILD + 'artifact/'),
                new CoverageEvidence(CheckStatus.PASS, 82.5d, 60.0d, 825L, 1000L),
                [new TestSuiteEvidence(TestStage.SMOKE, CheckStatus.PASS, 3L, 3L, 0L, 0L, 4500L)],
                [new ScanEvidence(EvidenceScanner.NEXUS_IQ, CheckStatus.WARN, 0L, 1L, 2L, 3L, 0L, 0L, 5L,
                        'https://tools.bbh.com/IQ/')],
                new ReleaseGateEvidence(false, 2L, 'SAST limits exceeded'),
                [new StageEvidence('Build', CheckStatus.PASS, 120L, null)])
        def gui = certScanner.services()[0]
        def body = new ProductEvidence(certScanner, [new ServiceEvidence(gui, [
                new PipelineEvidence(pipeline(id: 100L, serviceId: 10L), 'https://jenkins.test/job/gui-full/',
                        RunResult.SUCCESS, run),
                new PipelineEvidence(pipeline(id: 101L, serviceId: 10L, type: PipelineType.SAST,
                        keys: [revokedKey(reason: 'retired')]), null, RunResult.DISABLED, null)])], null)

        when:
        def response = mvc.perform(get('/api/evidence/products/1')).andReturn().response

        then:
        1 * evidence.product(1L) >> body
        response.status == 200
        with(parse(response.contentAsString)) {
            keySet() == ['productId', 'code', 'name', 'description', 'ownerTeam', 'contactEmail', 'services',
                         'metricsError'] as Set
            productId == 1
            code == 'CERT'
            name == 'CertScanner'
            ownerTeam == 'TA'
            contactEmail == 'ta@bbh.com'
            metricsError == null
            with(services[0]) {
                keySet() == ['serviceId', 'name', 'description', 'repositoryUrl', 'artifactName',
                             'appScanApplicationId', 'sonarProjectKey', 'nexusIqApplication', 'pipelines'] as Set
                serviceId == 10
                name == 'gui'
                description == 'Angular GUI'
                repositoryUrl == 'https://bitbucket.bbh.com/scm/cert/gui.git'
                artifactName == 'cert-gui.war'
                appScanApplicationId == APP_ID
                sonarProjectKey == 'cert-gui'
                nexusIqApplication == 'cert-gui'
                pipelines*.pipelineId == [100, 101]
                pipelines*.type == ['FULL', 'SAST']
                pipelines*.enabled == [true, false]
                pipelines[0].jenkinsJobUrl == 'https://jenkins.test/job/gui-full/'
                pipelines[1].status == 'DISABLED'
                pipelines[1].run == null
                with(pipelines[0].run) {
                    keySet() == ['build', 'coverage', 'testSuites', 'scans', 'releaseGate', 'stages'] as Set
                    build == [number       : 42, finishedAt: '2026-10-04T10:00:00Z', result: 'SUCCESS',
                              branch       : 'develop', commit: 'a1b2c3d', durationSeconds: 900, job: 'gui-full',
                              url          : buildUrl, reportUrl: buildUrl + 'Pipeline_20Report/',
                              testReportUrl: buildUrl + 'testReport/', artifactsUrl: buildUrl + 'artifact/']
                    coverage == [status: 'PASS', linePercent: 82.5, requiredPercent: 60.0, coveredLines: 825,
                                 totalLines: 1000]
                    testSuites == [[stage : 'SMOKE', status: 'PASS', jobs: 3, passed: 3, failed: 0, notConfigured: 0,
                                    durationMs: 4500]]
                    scans[0] == [scanner : 'NEXUS_IQ', status: 'WARN', critical: 0, high: 1, medium: 2, low: 3,
                                 maxCritical: 0, maxHigh: 0, maxMedium: 5, link: 'https://tools.bbh.com/IQ/']
                    releaseGate == [allowed: false, violations: 2, reason: 'SAST limits exceeded']
                    stages == [[name: 'Build', status: 'PASS', durationSeconds: 120, reason: null]]
                }
            }
        }
    }

    def "a run without a release gate decision shows none"() {
        given:
        def gui = certScanner.services()[0]
        def run = new RunEvidenceReport(new BuildEvidence(1L, Instant.parse('2026-10-04T10:00:00Z'), RunResult.FAILURE,
                null, null, null, null, null, null, null, null), new CoverageEvidence(CheckStatus.NO_DATA, null, null,
                null, null), [], [], null, [])

        when:
        def response = mvc.perform(get('/api/evidence/products/1')).andReturn().response

        then:
        1 * evidence.product(1L) >> new ProductEvidence(certScanner, [new ServiceEvidence(gui, [
                new PipelineEvidence(pipeline(id: 100L, serviceId: 10L), null, RunResult.FAILURE, run)])],
                'InfluxDB could not be read: timeout')
        with(parse(response.contentAsString)) {
            metricsError == 'InfluxDB could not be read: timeout'
            services[0].pipelines[0].run.releaseGate == null
            services[0].pipelines[0].run.build.url == null
        }
    }

    def "the evidence of an unknown product is 404"() {
        when:
        def response = mvc.perform(get('/api/evidence/products/9')).andReturn().response

        then:
        1 * evidence.product(9L) >> { throw NotFoundException.of('Product', 9L) }
        response.status == 404
        parse(response.contentAsString).detail == 'Product 9 does not exist'
    }
}
