package com.bbh.itss.dso.portal.application.dsoconfig

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublicationLockPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublishedConfigRepositoryPort
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.dsoconfig.DsoConfigBuilder
import com.bbh.itss.dso.portal.domain.dsoconfig.PublishedConfig
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class PipelineConfigPublisherSpec extends Specification {

    static final Instant EARLIER = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant NOW = Instant.parse('2026-10-04T12:00:00.123456789Z')
    static final Instant NOW_IN_MICROS = Instant.parse('2026-10-04T12:00:00.123456Z')

    ProductRepositoryPort products = Mock()
    PipelineRepositoryPort pipelines = Mock()
    PublishedConfigRepositoryPort published = Mock()
    GlobalSettingsRepositoryPort settings = Stub() {
        load() >> Optional.of(storedSettings())
    }
    PublicationLockPort lock = Mock()
    def json = JsonMapper.builder().build()
    ConfigSerializerPort serializer = [toJson  : { Map config -> json.writeValueAsString(config) },
                                       fromJson: { String text -> json.readValue(text, LinkedHashMap) }] as ConfigSerializerPort
    def builder = new DsoConfigBuilder(storedSettings().values())

    def publisher = new PipelineConfigPublisher(products, pipelines, settings, published, serializer, lock,
            Clock.fixed(NOW, ZoneOffset.UTC))

    Product certScanner = product(id: 1L, services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])
    Service gui = certScanner.services()[0]
    Pipeline guiFull = pipeline(id: 100L, serviceId: 10L)
    Pipeline guiSecurity = pipeline(id: 101L, serviceId: 10L, type: PipelineType.SECURITY,
            extendedPipelineJob: 'CERT/gui-extended')
    Pipeline apiFull = pipeline(id: 102L, serviceId: 11L)

    def setup() {
        products.load(1L) >> Optional.of(certScanner)
        products.findAll() >> [certScanner]
    }

    def "the configurations are locked through the publication lock before #change reads anything"() {
        when:
        action(publisher)

        then:
        1 * lock.lock()

        then:
        (0..1) * pipelines.load(100L) >> Optional.empty()
        (0..1) * products.load(1L) >> Optional.empty()
        (0..1) * products.findAll() >> []
        (0..1) * pipelines.findAll() >> []
        0 * published._

        where:
        change               | action
        'locking alone'      | { PipelineConfigPublisher it -> it.lockConfigurations() }
        'a changed pipeline' | { PipelineConfigPublisher it -> it.pipelineChanged(100L) }
        'a changed product'  | { PipelineConfigPublisher it -> it.productChanged(1L) }
        'changed settings'   | { PipelineConfigPublisher it -> it.settingsChanged() }
        'the start-up'       | { PipelineConfigPublisher it -> it.publishAll() }
    }

    def "a changed pipeline publishes its configuration as JSON with the time it was rendered over #stored"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)
        published.load(100L) >> [nothing: Optional.empty(),
                                 'another configuration': Optional.of(new PublishedConfig(100L, '{}', EARLIER)),
                                 'the same configuration': Optional.of(new PublishedConfig(100L, rendered(guiFull), EARLIER))][stored]

        when:
        publisher.pipelineChanged(100L)

        then:
        saves * published.save(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS))

        where:
        stored                   || saves
        'nothing'                || 1
        'another configuration'  || 1
        'the same configuration' || 0
    }

    def "#missing publishes nothing"() {
        given:
        pipelines.load(100L) >> Optional.empty()
        pipelines.load(200L) >> Optional.of(pipeline(id: 200L, productId: 2L, serviceId: 20L))
        pipelines.load(103L) >> Optional.of(pipeline(id: 103L, serviceId: 12L))
        products.load(2L) >> Optional.empty()

        when:
        action(publisher)

        then:
        0 * published._

        where:
        missing                                     | action
        'a pipeline that no longer exists'          | { PipelineConfigPublisher it -> it.pipelineChanged(100L) }
        'a pipeline of a product that is gone'      | { PipelineConfigPublisher it -> it.pipelineChanged(200L) }
        'a product that no longer exists'           | { PipelineConfigPublisher it -> it.productChanged(2L) }
        'a pipeline whose service left its product' | { PipelineConfigPublisher it -> it.pipelineChanged(103L) }
    }

    def "a changed product publishes each of its pipelines at the same time"() {
        given:
        List<PublishedConfig> saved = []
        published.load(_) >> Optional.empty()

        when:
        publisher.productChanged(1L)

        then:
        1 * pipelines.findByProductId(1L) >> [guiFull, guiSecurity, apiFull]
        3 * published.save(_) >> { PublishedConfig config -> saved << config }
        saved*.pipelineId() == [100L, 101L, 102L]
        saved*.renderedAt().unique() == [NOW_IN_MICROS]
        parse(saved[1].configJson()).pipeline.type == 'security'
        parse(saved[2].configJson()).projects.keySet() as List == ['backend-api']
    }

    def "without stored global settings only a product without pipelines publishes, which needs none"() {
        given:
        def withoutSettings = new PipelineConfigPublisher(products, pipelines,
                Stub(GlobalSettingsRepositoryPort) { load() >> Optional.empty() }, published, serializer, lock,
                Clock.systemUTC())
        pipelines.load(100L) >> Optional.of(guiFull)
        pipelines.findByProductId(1L) >> []

        when:
        withoutSettings.productChanged(1L)

        then:
        0 * published._

        when:
        withoutSettings.pipelineChanged(100L)

        then:
        thrown(MissingGlobalSettingsException)
    }

    def "changed global settings publish every pipeline, writing only what changed"() {
        given:
        published.load(100L) >> Optional.of(new PublishedConfig(100L, rendered(guiFull), EARLIER))
        published.load(101L) >> Optional.of(new PublishedConfig(101L, '{}', EARLIER))
        published.load(102L) >> Optional.empty()

        when:
        publisher.settingsChanged()

        then:
        1 * pipelines.findAll() >> [guiFull, guiSecurity, apiFull]
        1 * published.save({ it.pipelineId() == 101L && parse(it.configJson()).pipeline.type == 'security' })
        1 * published.save({ it.pipelineId() == 102L })
        0 * published.save(_)
    }

    def "publishing everything stamps every configuration again, also an unchanged one, and counts them"() {
        given:
        published.load(100L) >> Optional.of(new PublishedConfig(100L, rendered(guiFull), EARLIER))
        published.load(102L) >> Optional.empty()

        when:
        def count = publisher.publishAll()

        then:
        1 * pipelines.findAll() >> [guiFull, apiFull, pipeline(id: 200L, productId: 2L, serviceId: 20L)]
        1 * published.save(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS))
        1 * published.save({ it.pipelineId() == 102L })
        0 * published.save(_)
        count == 3
    }

    def "only a configuration published since everything was published is current"() {
        given:
        pipelines.findAll() >> []
        published.load(100L) >>> [Optional.of(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS)),
                                  Optional.of(new PublishedConfig(100L, '{"pipeline":{"type":"full"}}', EARLIER)),
                                  Optional.empty()]

        when:
        def before = publisher.currentConfig(100L)
        publisher.publishAll()

        then:
        before.empty
        publisher.currentConfig(100L).get() == builder.pipelineConfig(certScanner, gui, guiFull)
        publisher.currentConfig(100L).empty
        publisher.currentConfig(100L).empty
    }

    private String rendered(Pipeline pipeline) {
        json.writeValueAsString(builder.pipelineConfig(certScanner, gui, pipeline))
    }
}
