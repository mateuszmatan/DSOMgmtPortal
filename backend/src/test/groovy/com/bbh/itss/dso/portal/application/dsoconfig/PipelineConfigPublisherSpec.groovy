package com.bbh.itss.dso.portal.application.dsoconfig

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort
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
import spock.lang.Subject
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
    def json = JsonMapper.builder().build()
    ConfigSerializerPort serializer = [toJson  : { Map config -> json.writeValueAsString(config) },
                                       fromJson: { String text -> json.readValue(text, LinkedHashMap) }] as ConfigSerializerPort
    def builder = new DsoConfigBuilder(storedSettings().values())

    @Subject
    def publisher = new PipelineConfigPublisher(products, pipelines, settings, published, serializer,
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

    def "a changed pipeline publishes its configuration as JSON with the time it was rendered"() {
        when:
        publisher.pipelineChanged(100L)

        then:
        1 * pipelines.load(100L) >> Optional.of(guiFull)
        1 * published.load(100L) >> Optional.empty()
        1 * published.save(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS))
    }

    def "the published JSON is the configuration the pipeline's key serves"() {
        given:
        PublishedConfig saved = null
        pipelines.load(101L) >> Optional.of(guiSecurity)
        published.load(101L) >> Optional.empty()

        when:
        publisher.pipelineChanged(101L)

        then:
        1 * published.save(_) >> { PublishedConfig config -> saved = config }
        with(parse(saved.configJson())) {
            keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
            pipeline == [type      : 'security', entryPoint: 'devSecOpsSecurityPipeline', product: 'CERT',
                         projectNames: 'gui', agentNames: ['linux-agent']]
            platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
            defaults.releaseGate.stateFile == 'release-gate.json'
            projects.gui.jenkins == [pipeline: [extendedPipeline: 'CERT/gui-extended']]
        }
    }

    def "a pipeline that no longer exists publishes nothing"() {
        when:
        publisher.pipelineChanged(100L)

        then:
        1 * pipelines.load(100L) >> Optional.empty()
        0 * published._
    }

    def "a pipeline of a product that no longer exists publishes nothing"() {
        when:
        publisher.pipelineChanged(200L)

        then:
        1 * pipelines.load(200L) >> Optional.of(pipeline(id: 200L, productId: 2L, serviceId: 20L))
        1 * products.load(2L) >> Optional.empty()
        0 * published._
    }

    def "an unchanged configuration is neither saved nor stamped again"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        publisher.pipelineChanged(100L)

        then:
        1 * published.load(100L) >> Optional.of(new PublishedConfig(100L, rendered(guiFull), EARLIER))
        0 * published.save(_)
    }

    def "a stored configuration that changed is replaced"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        publisher.pipelineChanged(100L)

        then:
        1 * published.load(100L) >> Optional.of(new PublishedConfig(100L, '{"pipeline":{"type":"full"}}', EARLIER))
        1 * published.save(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS))
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
        parse(saved[2].configJson()).projects.keySet() as List == ['backend-api']
    }

    def "a product without pipelines publishes nothing and needs no settings"() {
        given:
        def withoutSettings = new PipelineConfigPublisher(products, pipelines,
                Stub(GlobalSettingsRepositoryPort) { load() >> Optional.empty() }, published, serializer,
                Clock.systemUTC())

        when:
        withoutSettings.productChanged(1L)

        then:
        1 * pipelines.findByProductId(1L) >> []
        0 * published._
    }

    def "a product that no longer exists publishes nothing"() {
        when:
        publisher.productChanged(2L)

        then:
        1 * products.load(2L) >> Optional.empty()
        0 * pipelines._
        0 * published._
    }

    def "a pipeline whose service has left its product publishes nothing"() {
        when:
        publisher.pipelineChanged(103L)

        then:
        1 * pipelines.load(103L) >> Optional.of(pipeline(id: 103L, serviceId: 12L))
        0 * published._
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

    def "publishing everything renders every pipeline and counts them"() {
        given:
        published.load(_) >> Optional.empty()

        when:
        def count = publisher.publishAll()

        then:
        count == 3
        1 * pipelines.findAll() >> [guiFull, apiFull, pipeline(id: 200L, productId: 2L, serviceId: 20L)]
        1 * published.save({ it.pipelineId() == 100L })
        1 * published.save({ it.pipelineId() == 102L })
        0 * published.save(_)
    }

    def "publishing everything stamps every configuration again, also an unchanged one"() {
        given:
        published.load(100L) >> Optional.of(new PublishedConfig(100L, rendered(guiFull), EARLIER))

        when:
        publisher.publishAll()

        then:
        1 * pipelines.findAll() >> [guiFull]
        1 * published.save(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS))
    }

    def "no published configuration is current before everything was published once"() {
        when:
        def current = publisher.currentConfig(100L)

        then:
        current.empty
        0 * published._
    }

    def "a configuration published since everything was published is current"() {
        given:
        pipelines.findAll() >> []
        publisher.publishAll()

        when:
        def current = publisher.currentConfig(100L)

        then:
        1 * published.load(100L) >> Optional.of(new PublishedConfig(100L, rendered(guiFull), NOW_IN_MICROS))
        current.get() == builder.pipelineConfig(certScanner, gui, guiFull)
        current.get().keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
    }

    def "a configuration published before everything was published, or none, is not current"() {
        given:
        pipelines.findAll() >> []
        publisher.publishAll()

        when:
        def current = publisher.currentConfig(100L)

        then:
        1 * published.load(100L) >> stored
        current.empty

        where:
        stored << [Optional.of(new PublishedConfig(100L, '{"pipeline":{"type":"full"}}', EARLIER)), Optional.empty()]
    }

    def "a portal without pipelines publishes nothing"() {
        when:
        def count = publisher.publishAll()

        then:
        count == 0
        1 * pipelines.findAll() >> []
        0 * published._
    }

    def "publishing without stored global settings is an error of the start-up"() {
        given:
        def withoutSettings = new PipelineConfigPublisher(products, pipelines,
                Stub(GlobalSettingsRepositoryPort) { load() >> Optional.empty() }, published, serializer,
                Clock.systemUTC())
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        withoutSettings.pipelineChanged(100L)

        then:
        def e = thrown(MissingGlobalSettingsException)
        e.message == 'The global settings are missing; the portal creates them at start-up'
    }

    private String rendered(Pipeline pipeline) {
        json.writeValueAsString(builder.pipelineConfig(certScanner, gui, pipeline))
    }
}
