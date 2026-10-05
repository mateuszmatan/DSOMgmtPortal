package com.bbh.itss.dso.portal.dsoconfig

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification
import spock.lang.Subject
import tools.jackson.databind.json.JsonMapper

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class PipelineConfigPublisherSpec extends Specification {

    static final Instant EARLIER = Instant.parse('2026-10-01T08:00:00Z')

    ProductRepositoryPort products = Mock()
    PipelineRepositoryPort pipelines = Mock()
    PublishedPipelineConfigRepository published = Mock()
    GlobalSettingsRepositoryPort settings = Stub() {
        load() >> Optional.of(storedSettings())
    }
    def builder = new DsoConfigBuilder(settings)
    def json = JsonMapper.builder().build()

    @Subject
    def publisher = new PipelineConfigPublisher(products, pipelines, published, builder, json)

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

    def "a changed pipeline publishes its configuration as JSON in a new row"() {
        given:
        def before = Instant.now()

        when:
        publisher.pipelineChanged(100L)

        then:
        1 * pipelines.load(100L) >> Optional.of(guiFull)
        1 * published.findById(100L) >> Optional.empty()
        1 * published.save({ PublishedPipelineConfig config ->
            config.pipelineId == 100L && config.id == 100L && config.isNew() &&
                    config.configJson == json.writeValueAsString(builder.pipelineConfig(certScanner, gui, guiFull)) &&
                    !config.renderedAt.isBefore(before.minusSeconds(1)) && !config.renderedAt.isAfter(Instant.now())
        })
    }

    def "the published JSON is the configuration the pipeline's key serves"() {
        given:
        PublishedPipelineConfig saved = null
        pipelines.load(101L) >> Optional.of(guiSecurity)
        published.findById(101L) >> Optional.empty()

        when:
        publisher.pipelineChanged(101L)

        then:
        1 * published.save(_) >> { PublishedPipelineConfig config -> saved = config }
        with(parse(saved.configJson)) {
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

    def "an unchanged configuration is neither saved nor stamped again"() {
        given:
        def stored = stored(100L, json.writeValueAsString(builder.pipelineConfig(certScanner, gui, guiFull)))
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        publisher.pipelineChanged(100L)

        then:
        1 * published.findById(100L) >> Optional.of(stored)
        0 * published.save(_)
        stored.renderedAt == EARLIER
    }

    def "a stored configuration that changed is updated in place, written when the transaction commits"() {
        given:
        def stored = stored(100L, '{"pipeline":{"type":"full"}}')
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        publisher.pipelineChanged(100L)

        then:
        1 * published.findById(100L) >> Optional.of(stored)
        0 * published.save(_)
        stored.configJson == json.writeValueAsString(builder.pipelineConfig(certScanner, gui, guiFull))
        stored.renderedAt.isAfter(EARLIER)
    }

    def "a changed product publishes each of its pipelines at the same time"() {
        given:
        List<PublishedPipelineConfig> saved = []
        published.findById(_) >> Optional.empty()

        when:
        publisher.productChanged(1L)

        then:
        1 * pipelines.findByProductId(1L) >> [guiFull, guiSecurity, apiFull]
        3 * published.save(_) >> { PublishedPipelineConfig config -> saved << config; config }
        saved*.pipelineId == [100L, 101L, 102L]
        saved*.renderedAt.unique().size() == 1
        parse(saved[2].configJson).projects.keySet() as List == ['backend-api']
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
        def unchanged = stored(100L, json.writeValueAsString(builder.pipelineConfig(certScanner, gui, guiFull)))
        def outdated = stored(101L, '{}')
        published.findById(100L) >> Optional.of(unchanged)
        published.findById(101L) >> Optional.of(outdated)
        published.findById(102L) >> Optional.empty()

        when:
        publisher.settingsChanged()

        then:
        1 * pipelines.findAll() >> [guiFull, guiSecurity, apiFull]
        1 * published.save({ it.pipelineId == 102L })
        0 * published.save(_)
        unchanged.renderedAt == EARLIER
        outdated.renderedAt.isAfter(EARLIER)
        parse(outdated.configJson).pipeline.type == 'security'
    }

    def "publishing everything renders every pipeline and counts them"() {
        given:
        published.findById(_) >> Optional.empty()

        when:
        def count = publisher.publishAll()

        then:
        count == 2
        1 * pipelines.findAll() >> [guiFull, apiFull]
        1 * published.save({ it.pipelineId == 100L })
        1 * published.save({ it.pipelineId == 102L })
    }

    def "a portal without pipelines publishes nothing"() {
        when:
        def count = publisher.publishAll()

        then:
        count == 0
        1 * pipelines.findAll() >> []
        0 * published._
    }

    private static PublishedPipelineConfig stored(Long pipelineId, String configJson) {
        def config = new PublishedPipelineConfig(pipelineId)
        config.publish(configJson, EARLIER)
        config.markStored()
        config
    }
}
