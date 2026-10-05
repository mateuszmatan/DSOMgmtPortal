package com.bbh.itss.dso.portal.evidence

import com.bbh.itss.dso.portal.adapter.in.web.ApiExceptionHandler
import com.bbh.itss.dso.portal.catalog.TestStage
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.BuildEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.CoverageEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.PipelineEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ProductEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ReleaseGateEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.RunEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ScanEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ServiceEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.StageEvidence
import com.bbh.itss.dso.portal.evidence.EvidenceDtos.TestSuiteEvidence
import com.bbh.itss.dso.portal.monitoring.RunResult
import com.bbh.itss.dso.portal.pipeline.PipelineType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class EvidenceControllerSpec extends Specification {

    static final String BUILD = 'https://jenkins.test/job/gui-full/42/'

    EvidenceService evidence = Mock()
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new EvidenceController(evidence))
            .setControllerAdvice(new ApiExceptionHandler())
            .build()

    def "a product's evidence lists each service's pipelines with their latest run"() {
        given:
        String buildUrl = BUILD
        def run = new RunEvidence(
                new BuildEvidence(42L, Instant.parse('2026-10-04T10:00:00Z'), RunResult.SUCCESS, 'develop', 'a1b2c3d', 900L,
                        'gui-full', BUILD, BUILD + 'Pipeline_20Report/', BUILD + 'testReport/', BUILD + 'artifact/'),
                new CoverageEvidence(CheckStatus.PASS, 82.5d, 60.0d, 825L, 1000L),
                [new TestSuiteEvidence(TestStage.SMOKE, CheckStatus.PASS, 3L, 3L, 0L, 0L, 4500L)],
                [new ScanEvidence(EvidenceScanner.NEXUS_IQ, CheckStatus.WARN, 0L, 1L, 2L, 3L, 0L, 0L, 5L,
                        'https://tools.bbh.com/IQ/')],
                new ReleaseGateEvidence(false, 2L, 'SAST limits exceeded'),
                [new StageEvidence('Build', CheckStatus.PASS, 120L, null)])
        def body = new ProductEvidence(1L, 'CERT', 'CertScanner', null, 'TA', 'ta@bbh.com', [
                new ServiceEvidence(10L, 'gui', null, 'https://bitbucket.bbh.com/scm/cert/gui.git', 'cert-gui.war', 'app-1',
                        'cert-gui', 'cert-gui', [
                        new PipelineEvidence(100L, PipelineType.FULL, true, 'https://jenkins.test/job/gui-full/',
                                RunResult.SUCCESS, run),
                        new PipelineEvidence(101L, PipelineType.SAST, false, null, RunResult.DISABLED, null)])], null)

        when:
        def response = mvc.perform(get('/api/evidence/products/1')).andReturn().response

        then:
        1 * evidence.product(1L) >> body
        response.status == 200
        with(parse(response.contentAsString)) {
            code == 'CERT'
            metricsError == null
            with(services[0]) {
                name == 'gui'
                nexusIqApplication == 'cert-gui'
                pipelines*.type == ['FULL', 'SAST']
                pipelines[1].status == 'DISABLED'
                pipelines[1].run == null
                with(pipelines[0].run) {
                    build.number == 42
                    build.finishedAt == '2026-10-04T10:00:00Z'
                    build.url == buildUrl
                    build.reportUrl == buildUrl + 'Pipeline_20Report/'
                    coverage == [status: 'PASS', linePercent: 82.5, requiredPercent: 60.0, coveredLines: 825, totalLines: 1000]
                    testSuites[0].stage == 'SMOKE'
                    scans[0] == [scanner : 'NEXUS_IQ', status: 'WARN', critical: 0, high: 1, medium: 2, low: 3, maxCritical: 0,
                                 maxHigh : 0, maxMedium: 5, link: 'https://tools.bbh.com/IQ/']
                    releaseGate == [allowed: false, violations: 2, reason: 'SAST limits exceeded']
                    stages == [[name: 'Build', status: 'PASS', durationSeconds: 120, reason: null]]
                }
            }
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
