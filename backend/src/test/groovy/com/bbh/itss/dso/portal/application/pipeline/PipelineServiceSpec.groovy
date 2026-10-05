package com.bbh.itss.dso.portal.application.pipeline

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineCommand
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.pipeline.KeyRevokedException
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.support.Fixtures.KEY
import static com.bbh.itss.dso.portal.support.Fixtures.activeKey
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class PipelineServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-05T07:30:00.123456789Z')
    static final Instant NOW_MICROS = Instant.parse('2026-10-05T07:30:00.123456Z')
    static final String NEW_KEY = '9b2e2a52-2f0d-4f53-9b55-4f0b9a8f6c11'

    PipelineRepositoryPort pipelines = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ProductRepositoryPort products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }
    PublishPipelineConfigsUseCase publisher = Mock()
    KeyGenerator keys = { -> NEW_KEY } as KeyGenerator

    @Subject
    def service = new PipelineService(pipelines, products, settings, publisher, keys,
            Clock.fixed(NOW, ZoneOffset.UTC))

    def certScanner = product(id: 1, services: [[name: 'gui', id: 10], [name: 'backend-api', id: 11]])

    def "each service of a product is listed with its pipelines"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [pipeline(id: 100), pipeline(id: 101, type: PipelineType.SAST)]

        when:
        def list = service.listForProduct(1L)

        then:
        list*.service()*.name() == ['gui', 'backend-api']
        list[0].pipelines()*.pipeline()*.id() == [100, 101]
        list[0].pipelines()*.jenkinsUrl() == ['https://jenkins.test', 'https://jenkins.test']
        list[1].pipelines() == []
    }

    def "pipelines of an unknown product are not found"() {
        when:
        service.listForProduct(1L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Product 1 does not exist'
    }

    def "a single pipeline comes with its product, service and the Jenkins address"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100, keys: [activeKey(), revokedKey()]))
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.get(100L)

        then:
        view.product().code() == 'CERT'
        view.service().name() == 'gui'
        view.pipeline().keys().size() == 2
        view.jenkinsUrl() == 'https://jenkins.test'
        view.influxProjectTag() == 'CERT-gui'
        view.influxEnv() == 'test'
    }

    def "an unknown pipeline is not found"() {
        when:
        service.get(100L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Pipeline 100 does not exist'
    }

    def "a pipeline whose product is gone is not found"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100))

        when:
        service.get(100L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Product 1 does not exist'
    }

    def "a pipeline is created with its first key and published"() {
        given:
        products.findByServiceId(10L) >> Optional.of(certScanner)

        when:
        def view = service.create(10L, new PipelineCommand(PipelineType.SECURITY, pipelineSettings(
                agentLabels: ['linux'], extendedPipelineJob: 'CERT/gui-extended', jenkinsJob: 'DevSecOps/CERT/gui-security')))

        then:
        1 * pipelines.save({ Pipeline p ->
            p.id() == null && p.type() == PipelineType.SECURITY && p.service().serviceId() == 10L &&
                    p.service().productId() == 1L && p.keys()*.value() == [NEW_KEY] && p.keys()[0].issuedAt() == NOW_MICROS
        }) >> { Pipeline p -> stored(100L, p) }

        then:
        1 * publisher.pipelineChanged(100L)
        view.pipeline().id() == 100
        view.pipeline().settings().extendedPipelineJob() == 'CERT/gui-extended'
        view.jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-security/'
        view.pipeline().activeKey().get().status() == KeyStatus.ACTIVE
    }

    def "a service has at most one pipeline of each type"() {
        given:
        products.findByServiceId(10L) >> Optional.of(certScanner)
        pipelines.existsForService(10L, PipelineType.FULL) >> true

        when:
        service.create(10L, new PipelineCommand(PipelineType.FULL, pipelineSettings()))

        then:
        def e = thrown(ConflictException)
        e.message == 'Service gui already has a full pipeline'
        0 * pipelines.save(_)
        0 * publisher._
    }

    def "a pipeline needs an existing service"() {
        when:
        service.create(10L, new PipelineCommand(PipelineType.FULL, pipelineSettings()))

        then:
        def e = thrown(NotFoundException)
        e.message == 'Service 10 does not exist'
    }

    def "a product that no longer lists the service does not get a pipeline for it"() {
        given:
        products.findByServiceId(12L) >> Optional.of(certScanner)

        when:
        service.create(12L, new PipelineCommand(PipelineType.FULL, pipelineSettings()))

        then:
        def e = thrown(NotFoundException)
        e.message == 'Service 12 does not exist'
    }

    def "a pipeline's settings can change and the pipeline is published"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100))
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.update(100L, new PipelineCommand(PipelineType.FULL, pipelineSettings(
                agentLabels: ['windows', 'linux'], extendedPipelineJob: 'CERT/x', securityPipelineJob: 'CERT/y',
                description: 'Nightly')))

        then:
        1 * pipelines.save({ Pipeline p -> p.settings().agentLabels() == ['windows', 'linux'] }) >> { Pipeline p -> p }

        then:
        1 * publisher.pipelineChanged(100L)
        view.pipeline().settings().description() == 'Nightly'
        view.pipeline().settings().extendedPipelineJob() == null
        view.pipeline().settings().securityPipelineJob() == null
    }

    def "a pipeline's type cannot change"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100))

        when:
        service.update(100L, new PipelineCommand(PipelineType.SAST, pipelineSettings()))

        then:
        thrown(ConflictException)
        0 * pipelines.save(_)
        0 * publisher._
    }

    def "a pipeline is deleted with its keys"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100))

        when:
        service.delete(100L)

        then:
        1 * pipelines.delete(100L)
    }

    def "deleting an unknown pipeline fails"() {
        when:
        service.delete(100L)

        then:
        thrown(NotFoundException)
        0 * pipelines.delete(_)
    }

    def "a key is revoked on the pipeline loaded under a row lock"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.revokeKey(100L, 'Leaked in a build log')

        then:
        1 * pipelines.loadForUpdate(100L) >> Optional.of(pipeline(id: 100))
        0 * pipelines.load(_)

        then:
        1 * pipelines.save({ Pipeline p -> !p.enabled }) >> { Pipeline p -> p }
        0 * publisher._
        !view.pipeline().enabled
        view.pipeline().keys()[0].revokeReason() == 'Leaked in a build log'
        view.pipeline().keys()[0].revokedAt() == NOW_MICROS
    }

    def "a new key is issued on the pipeline loaded under a row lock"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.issueKey(100L)

        then:
        1 * pipelines.loadForUpdate(100L) >> Optional.of(pipeline(id: 100))

        then:
        1 * pipelines.save(_) >> { Pipeline p -> p }
        0 * publisher._
        view.pipeline().activeKey().get().value() == NEW_KEY
        view.pipeline().keys()*.status() == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
    }

    def "keys of an unknown pipeline cannot change"() {
        when:
        service.issueKey(100L)

        then:
        thrown(NotFoundException)
        0 * pipelines.save(_)
    }

    def "an active key resolves to its pipeline and its use is recorded"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        PipelineView view = service.resolveKey(" ${KEY.toUpperCase()} ")

        then:
        1 * pipelines.findByKey(KEY) >> Optional.of(pipeline(id: 100))
        1 * pipelines.recordKeyUse(100L, NOW_MICROS)
        0 * pipelines.save(_)
        view.pipeline().id() == 100
        view.pipeline().activeKey().get().lastUsedAt() == NOW_MICROS
    }

    def "a revoked key is refused with the reason and its use is not recorded"() {
        given:
        def revoked = revokedKey(reason: 'Service retired')
        pipelines.findByKey(revoked.value()) >> Optional.of(pipeline(id: 100, keys: [revoked]))

        when:
        service.resolveKey(revoked.value())

        then:
        def e = thrown(KeyRevokedException)
        e.message == "The DevSecOps pipeline key was invalidated on ${revoked.revokedAt()}: Service retired"
        0 * pipelines.recordKeyUse(*_)
    }

    def "a key that was never issued is not found"() {
        when:
        service.resolveKey('00000000-0000-4000-8000-000000000000')

        then:
        def e = thrown(NotFoundException)
        e.message == 'Unknown DevSecOps pipeline key'
    }

    private static Pipeline stored(long id, Pipeline pipeline) {
        Pipeline.restore(id, pipeline.service(), pipeline.type(), pipeline.settings(),
                pipeline.keys().collect { k -> activeKey(id: 500L, value: k.value(), issuedAt: k.issuedAt()) },
                0, NOW, NOW)
    }
}
