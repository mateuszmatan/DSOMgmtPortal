package com.bbh.itss.dso.portal.application.pipeline

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.pipeline.IssuedKey
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.pipeline.KeyRevokedException
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification

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
    def service = new PipelineService(pipelines, products, settings, publisher, keys, Clock.fixed(NOW, ZoneOffset.UTC))

    def certScanner = product(id: 1, services: [[name: 'gui', id: 10], [name: 'backend-api', id: 11]])

    def "each service of a product is listed with its pipelines, and one pipeline comes with its product and service"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [pipeline(id: 100), pipeline(id: 101, type: PipelineType.SAST)]
        pipelines.load(100L) >> Optional.of(pipeline(id: 100, keys: [activeKey(), revokedKey()]))

        when:
        def list = service.listForProduct(1L)
        def view = service.get(100L)

        then:
        list*.service()*.name() == ['gui', 'backend-api']
        list[0].pipelines()*.pipeline()*.id() == [100, 101]
        list[0].pipelines()*.jenkinsUrl() == ['https://jenkins.test', 'https://jenkins.test']
        list[1].pipelines() == []
        [view.product().code(), view.service().name(), view.pipeline().keys().size(), view.jenkinsUrl()] ==
                ['CERT', 'gui', 2, 'https://jenkins.test']
        [view.influxProjectTag(), view.influxEnv()] == ['CERT-gui', 'test']
    }

    def "#action finds no #missing"() {
        given:
        pipelines.load(101L) >> Optional.of(pipeline(id: 101, productId: 2))
        products.load(1L) >> Optional.of(certScanner)
        products.findByServiceId(12L) >> Optional.of(certScanner)

        when:
        call(service)

        then:
        def e = thrown(NotFoundException)
        e.message == "$missing does not exist"
        0 * pipelines.save(_)
        0 * pipelines.delete(_)

        where:
        action                  | missing        | call
        'listing'               | 'Product 2'    | { it.listForProduct(2L) }
        'reading'               | 'Pipeline 100' | { it.get(100L) }
        'reading'               | 'Product 2'    | { it.get(101L) }
        'starting new services' | 'Service 12'   | { it.createForNewServices(1L, [12L]) }
        'starting new services' | 'Product 7'    | { it.createForNewServices(7L, [10L]) }
        'creating'              | 'Service 10'   | { it.create(10L, PipelineType.FULL, pipelineSettings()) }
        'creating'              | 'Service 12'   | { it.create(12L, PipelineType.FULL, pipelineSettings()) }
        'deleting'              | 'Pipeline 100' | { it.delete(100L) }
        'issuing a key'         | 'Pipeline 100' | { it.issueKey(100L) }
    }

    def "a pipeline is created with its first key and published"() {
        given:
        products.findByServiceId(10L) >> Optional.of(certScanner)

        when:
        def view = service.create(10L, PipelineType.SECURITY, pipelineSettings(
                agentLabels: ['linux'], extendedPipelineJob: 'CERT/gui-extended', jenkinsJob: 'DevSecOps/CERT/gui-security'))

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

    def "every service a save created starts with a full pipeline, a key and a published configuration"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        def views = service.createForNewServices(1L, [10L, 11L])

        then:
        1 * publisher.lockConfigurations()

        then:
        2 * pipelines.save({ Pipeline p ->
            p.type() == PipelineType.FULL && p.settings().agentLabels() == ['linux-agent'] &&
                    p.settings().jenkinsJob() == null && p.keys()*.value() == [NEW_KEY]
        }) >>> [stored(100L, pipeline(id: null, serviceId: 10L)), stored(101L, pipeline(id: null, serviceId: 11L))]
        1 * publisher.pipelineChanged(100L)
        1 * publisher.pipelineChanged(101L)
        views*.pipeline()*.id() == [100L, 101L]
        views*.service()*.name() == ['gui', 'backend-api']
        views.every { it.pipeline().isEnabled() }
    }

    def "a service that already has a full pipeline keeps it, and a save that created no service takes no lock"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.existsForService(10L, PipelineType.FULL) >> true

        when:
        def views = service.createForNewServices(1L, [10L, 11L])

        then:
        1 * pipelines.save({ Pipeline p -> p.service().serviceId() == 11L }) >> { Pipeline p -> stored(101L, p) }
        0 * pipelines.save({ Pipeline p -> p.service().serviceId() == 10L })
        views*.pipeline()*.id() == [101L]

        when:
        def none = service.createForNewServices(1L, [])

        then:
        none == []
        0 * publisher.lockConfigurations()
        0 * products.load(_)
        0 * pipelines.save(_)
    }

    def "#refusal is refused before anything is saved or published"() {
        given:
        products.findByServiceId(10L) >> Optional.of(certScanner)
        pipelines.existsForService(10L, PipelineType.FULL) >> true
        pipelines.load(100L) >> Optional.of(pipeline(id: 100))

        when:
        action(service)

        then:
        def e = thrown(ConflictException)
        e.message == message
        0 * pipelines.save(_)
        0 * publisher.pipelineChanged(_)

        where:
        refusal                       | action                                                     || message
        'a second pipeline of a type' | { it.create(10L, PipelineType.FULL, pipelineSettings()) }  || 'Service gui already has a full pipeline'
        'a change of the type'        | { it.update(100L, PipelineType.SAST, pipelineSettings()) } || 'The type of a pipeline cannot change; add a new pipeline instead'
    }

    def "a pipeline's settings can change and the pipeline is published"() {
        given:
        pipelines.load(100L) >> Optional.of(pipeline(id: 100))
        products.load(1L) >> Optional.of(certScanner)

        when:
        def view = service.update(100L, PipelineType.FULL, pipelineSettings(
                agentLabels: ['windows', 'linux'], extendedPipelineJob: 'CERT/x', securityPipelineJob: 'CERT/y',
                description: 'Nightly'))

        then:
        1 * pipelines.save({ Pipeline p -> p.settings().agentLabels() == ['windows', 'linux'] }) >> { Pipeline p -> p }

        then:
        1 * publisher.pipelineChanged(100L)
        view.pipeline().settings().description() == 'Nightly'
        view.pipeline().settings().extendedPipelineJob() == null
        view.pipeline().settings().securityPipelineJob() == null
    }

    def "a pipeline is created, changed and deleted only under the configuration lock, taken before anything is read"() {
        given:
        products.load(1L) >> Optional.of(certScanner)

        when:
        service.create(10L, PipelineType.FULL, pipelineSettings())

        then:
        1 * publisher.lockConfigurations()

        then:
        1 * products.findByServiceId(10L) >> Optional.of(certScanner)
        1 * pipelines.save(_) >> { Pipeline p -> stored(100L, p) }

        when:
        service.update(100L, PipelineType.FULL, pipelineSettings(agentLabels: ['windows']))

        then:
        1 * publisher.lockConfigurations()

        then:
        1 * pipelines.load(100L) >> Optional.of(pipeline(id: 100))
        1 * pipelines.save(_) >> { Pipeline p -> p }

        when:
        service.delete(100L)

        then:
        1 * publisher.lockConfigurations()

        then:
        1 * pipelines.load(100L) >> Optional.of(pipeline(id: 100))
        1 * pipelines.delete(100L)
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

    def "an active key given in any case and with spaces authorizes its pipeline and its use is recorded"() {
        when:
        long pipelineId = service.authorizeKey(" ${KEY.toUpperCase()} ")

        then:
        1 * pipelines.findKey(KEY) >> Optional.of(new IssuedKey(100L, activeKey(id: 500L)))
        1 * pipelines.recordKeyUse(500L, NOW_MICROS) >> true
        0 * pipelines.load(_)
        0 * pipelines.save(_)
        0 * products._
        pipelineId == 100L
    }

    def "a revoked key is refused with the reason and its use is not recorded"() {
        given:
        def revoked = revokedKey(reason: 'Service retired')
        pipelines.findKey(revoked.value()) >> Optional.of(new IssuedKey(100L, revoked))

        when:
        service.authorizeKey(revoked.value())

        then:
        def e = thrown(KeyRevokedException)
        e.message == "The DevSecOps pipeline key was invalidated on ${revoked.revokedAt()}: Service retired"
        0 * pipelines.recordKeyUse(*_)
    }

    def "a key whose use was not recorded is refused when it was #outcome meanwhile"() {
        when:
        service.authorizeKey(KEY)

        then:
        2 * pipelines.findKey(KEY) >>> [Optional.of(new IssuedKey(100L, activeKey(id: 500L))), reread]
        1 * pipelines.recordKeyUse(500L, NOW_MICROS) >> false
        def e = thrown(failure)
        e.message.endsWith(message)

        where:
        outcome       | reread                                                                     || failure             | message
        'invalidated' | Optional.of(new IssuedKey(100L, revokedKey(value: KEY, reason: 'Leaked'))) || KeyRevokedException | ': Leaked'
        'deleted'     | Optional.empty()                                                           || NotFoundException   | 'Unknown DevSecOps pipeline key'
    }

    def "a key that is still active after a use that was not recorded authorizes its pipeline"() {
        when:
        long pipelineId = service.authorizeKey(KEY)

        then:
        2 * pipelines.findKey(KEY) >> Optional.of(new IssuedKey(100L, activeKey(id: 500L)))
        1 * pipelines.recordKeyUse(500L, NOW_MICROS) >> false
        pipelineId == 100L
    }

    def "a key that was never issued is not found: '#value'"() {
        when:
        service.authorizeKey(value)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Unknown DevSecOps pipeline key'
        0 * pipelines.recordKeyUse(*_)

        where:
        value << ['00000000-0000-4000-8000-000000000000', ' ', null]
    }

    private static Pipeline stored(long id, Pipeline pipeline) {
        Pipeline.restore(id, pipeline.service(), pipeline.type(), pipeline.settings(),
                pipeline.keys().collect { k -> activeKey(id: 500L, value: k.value(), issuedAt: k.issuedAt()) },
                0, NOW, NOW)
    }
}
