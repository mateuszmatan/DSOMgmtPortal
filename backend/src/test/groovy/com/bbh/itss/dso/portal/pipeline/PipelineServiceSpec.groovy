package com.bbh.itss.dso.portal.pipeline

import com.bbh.itss.dso.portal.catalog.ProductRepository
import com.bbh.itss.dso.portal.catalog.ServiceDefinitionRepository
import com.bbh.itss.dso.portal.common.ConflictException
import com.bbh.itss.dso.portal.common.NotFoundException
import com.bbh.itss.dso.portal.settings.GlobalSettingsService
import org.spockframework.mock.EmptyOrDummyResponse
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static com.bbh.itss.dso.portal.support.Fixtures.withId

class PipelineServiceSpec extends Specification {

    PipelineRepository pipelines = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineKeyRepository keys = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ServiceDefinitionRepository services = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ProductRepository products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    GlobalSettingsService settings = Stub() {
        jenkinsUrl() >> 'https://jenkins.test'
    }
    ApplicationEventPublisher events = Mock()

    @Subject
    def pipelineService = new PipelineService(pipelines, keys, services, products, settings, events)

    def certScanner = product(id: 1)
    def gui = service(certScanner, name: 'gui', id: 10)
    def api = service(certScanner, name: 'backend-api', id: 11)

    def "each service of a product is listed with its pipelines"() {
        given:
        products.findById(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [pipeline(gui, id: 100), pipeline(gui, id: 101, type: PipelineType.SAST)]

        when:
        def list = pipelineService.listForProduct(1L)

        then:
        list*.serviceName == ['gui', 'backend-api']
        list[0].pipelines*.id == [100, 101]
        list[0].pipelines.every { it.keys == null && it.activeKey != null }
        list[0].buildTool == gui.build.tool()
        list[1].pipelines == []
    }

    def "pipelines of an unknown product are not found"() {
        when:
        pipelineService.listForProduct(1L)

        then:
        thrown(NotFoundException)
    }

    def "a single pipeline comes with its key history"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        pipeline.issueKey()
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline)

        when:
        def response = pipelineService.get(100L)

        then:
        response.keys.size() == 2
        response.activeKey.value == pipeline.activeKey().get().value
        response.productCode == 'CERT'
        response.serviceName == 'gui'
        response.entryPoint == 'devSecOpsPipeline'
        response.influxProjectTag == 'CERT-gui'
        response.enabled
    }

    def "an unknown pipeline is not found"() {
        when:
        pipelineService.get(100L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Pipeline 100 does not exist'
    }

    def "a pipeline is loaded with its service to render its config"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline)

        expect:
        pipelineService.pipeline(100L).is(pipeline)
        pipeline.activeKey().get().lastUsedAt == null
    }

    def "a pipeline is created with its first key"() {
        given:
        services.findById(10L) >> Optional.of(gui)

        when:
        def response = pipelineService.create(10L, new PipelineRequest(PipelineType.SECURITY, ['linux'], 'CERT/gui-extended', null,
                'DevSecOps/CERT/gui-security', null))

        then:
        1 * pipelines.saveAndFlush({ Pipeline p -> p.type == PipelineType.SECURITY && p.service.is(gui) }) >> { Pipeline p -> withId(p, 100L) }
        1 * events.publishEvent(new PipelineChanged(100L))
        response.id == 100
        response.extendedPipelineJob == 'CERT/gui-extended'
        response.jenkinsJob == 'DevSecOps/CERT/gui-security'
        response.jenkinsJobUrl == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-security/'
        response.keys.size() == 1
        response.activeKey.status == KeyStatus.ACTIVE
    }

    def "a service has at most one pipeline of each type"() {
        given:
        services.findById(10L) >> Optional.of(gui)
        pipelines.existsByServiceIdAndType(10L, PipelineType.FULL) >> true

        when:
        pipelineService.create(10L, new PipelineRequest(PipelineType.FULL, ['linux'], null, null, null, null))

        then:
        def e = thrown(ConflictException)
        e.message == 'Service gui already has a full pipeline'
        0 * pipelines.saveAndFlush(_)
    }

    def "a pipeline needs an existing service"() {
        when:
        pipelineService.create(10L, new PipelineRequest(PipelineType.FULL, ['linux'], null, null, null, null))

        then:
        def e = thrown(NotFoundException)
        e.message == 'Service 10 does not exist'
    }

    def "a pipeline's settings can change"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline)

        when:
        def response = pipelineService.update(100L, new PipelineRequest(PipelineType.FULL, ['windows', 'linux'], 'CERT/x', 'CERT/y', null, 'Nightly'))

        then:
        1 * pipelines.saveAndFlush(pipeline) >> pipeline
        1 * events.publishEvent(new PipelineChanged(100L))
        response.agentLabels == ['windows', 'linux']
        response.description == 'Nightly'
        response.extendedPipelineJob == null
        response.securityPipelineJob == null
    }

    def "a pipeline's type cannot change"() {
        given:
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline(gui, id: 100))

        when:
        pipelineService.update(100L, new PipelineRequest(PipelineType.SAST, ['linux'], null, null, null, null))

        then:
        thrown(ConflictException)
        0 * pipelines.saveAndFlush(_)
    }

    def "a pipeline is deleted with its keys"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline)

        when:
        pipelineService.delete(100L)

        then:
        1 * pipelines.delete(pipeline)
    }

    def "a key is revoked under a lock on the pipeline"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline)

        when:
        def response = pipelineService.revokeKey(100L, 'Leaked in a build log')

        then:
        1 * pipelines.findForUpdate(100L) >> Optional.of(pipeline)

        then:
        1 * pipelines.saveAndFlush(pipeline) >> pipeline
        !response.enabled
        response.activeKey == null
        response.keys[0].revokeReason == 'Leaked in a build log'
    }

    def "a new key is issued under a lock on the pipeline"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        def old = pipeline.activeKey().get().value
        pipelines.findWithServiceById(100L) >> Optional.of(pipeline)

        when:
        def response = pipelineService.issueKey(100L)

        then:
        1 * pipelines.findForUpdate(100L) >> Optional.of(pipeline)

        then:
        1 * pipelines.saveAndFlush(pipeline) >> pipeline
        response.activeKey.value != old
        response.keys*.status == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
    }

    def "keys of an unknown pipeline cannot change"() {
        when:
        pipelineService.issueKey(100L)

        then:
        thrown(NotFoundException)
        0 * pipelines.saveAndFlush(_)
    }

    def "an active key resolves to its pipeline and is marked as used"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        def key = pipeline.activeKey().get()

        when:
        def resolved = pipelineService.resolveKey(" ${key.value.toUpperCase()} ")

        then:
        1 * keys.findByValue(key.value) >> Optional.of(key)
        resolved.is(pipeline)
        key.lastUsedAt != null
    }

    def "a revoked key is refused with the reason"() {
        given:
        def pipeline = pipeline(gui, id: 100)
        def key = pipeline.revokeActiveKey('Service retired')
        keys.findByValue(key.value) >> Optional.of(key)

        when:
        pipelineService.resolveKey(key.value)

        then:
        def e = thrown(KeyRevokedException)
        e.message == "The DevSecOps pipeline key was invalidated on ${key.revokedAt}: Service retired"
        key.lastUsedAt == null
    }

    def "a key that was never issued is not found"() {
        when:
        pipelineService.resolveKey('00000000-0000-4000-8000-000000000000')

        then:
        def e = thrown(NotFoundException)
        e.message == 'Unknown DevSecOps pipeline key'
    }

    def "pipeline counts per product come from the repository"() {
        given:
        pipelines.countByProduct() >> [[1L, 3L] as Object[], [2, 1] as Object[]]
        pipelines.countWithActiveKeyByProduct() >> [[1L, 2L] as Object[]]
        def statistics = new PipelineRepositoryStatistics(pipelines)

        expect:
        statistics.pipelinesPerProduct() == [1L: 3L, 2L: 1L]
        statistics.activePipelinesPerProduct() == [1L: 2L]
    }
}
