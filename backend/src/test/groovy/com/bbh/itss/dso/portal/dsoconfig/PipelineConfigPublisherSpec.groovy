package com.bbh.itss.dso.portal.dsoconfig

import com.bbh.itss.dso.portal.catalog.ProductChanged
import com.bbh.itss.dso.portal.pipeline.PipelineChanged
import com.bbh.itss.dso.portal.pipeline.PipelineRepository
import com.bbh.itss.dso.portal.pipeline.PipelineType
import com.bbh.itss.dso.portal.settings.GlobalSettingsChanged
import com.bbh.itss.dso.portal.settings.GlobalSettingsService
import spock.lang.Specification
import spock.lang.Subject
import tools.jackson.databind.json.JsonMapper

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.globalSettings
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service

class PipelineConfigPublisherSpec extends Specification {

    static final Instant EARLIER = Instant.parse('2026-10-01T08:00:00Z')

    PipelineRepository pipelines = Mock()
    PublishedPipelineConfigRepository published = Mock()
    GlobalSettingsService settings = Stub() {
        values() >> globalSettings()
    }
    def builder = new DsoConfigBuilder(settings)
    def json = JsonMapper.builder().build()

    @Subject
    def publisher = new PipelineConfigPublisher(pipelines, published, builder, json)

    def certScanner = product(id: 1)
    def gui = service(certScanner, name: 'gui', id: 10)
    def api = service(certScanner, name: 'backend-api', id: 11)
    def guiFull = pipeline(gui, id: 100)
    def guiSecurity = pipeline(gui, id: 101, type: PipelineType.SECURITY, extendedPipelineJob: 'CERT/gui-extended')
    def apiFull = pipeline(api, id: 102)

    def "a changed pipeline publishes its configuration as JSON in a new row"() {
        given:
        def before = Instant.now()

        when:
        publisher.onPipelineChanged(new PipelineChanged(100L))

        then:
        1 * pipelines.findWithServiceById(100L) >> Optional.of(guiFull)
        1 * published.findById(100L) >> Optional.empty()
        1 * published.save({ PublishedPipelineConfig config ->
            config.pipelineId == 100L && config.id == 100L && config.isNew() &&
                    config.configJson == json.writeValueAsString(builder.pipelineConfig(guiFull)) &&
                    !config.renderedAt.isBefore(before.minusSeconds(1)) && !config.renderedAt.isAfter(Instant.now())
        })
    }

    def "the published JSON is the configuration the pipeline's key serves"() {
        given:
        PublishedPipelineConfig saved = null
        pipelines.findWithServiceById(101L) >> Optional.of(guiSecurity)
        published.findById(101L) >> Optional.empty()

        when:
        publisher.onPipelineChanged(new PipelineChanged(101L))

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
        publisher.onPipelineChanged(new PipelineChanged(100L))

        then:
        1 * pipelines.findWithServiceById(100L) >> Optional.empty()
        0 * published._
    }

    def "an unchanged configuration is neither saved nor stamped again"() {
        given:
        def stored = stored(100L, json.writeValueAsString(builder.pipelineConfig(guiFull)))
        pipelines.findWithServiceById(100L) >> Optional.of(guiFull)

        when:
        publisher.onPipelineChanged(new PipelineChanged(100L))

        then:
        1 * published.findById(100L) >> Optional.of(stored)
        0 * published.save(_)
        stored.renderedAt == EARLIER
    }

    def "a stored configuration that changed is updated in place, written when the transaction commits"() {
        given:
        def stored = stored(100L, '{"pipeline":{"type":"full"}}')
        pipelines.findWithServiceById(100L) >> Optional.of(guiFull)

        when:
        publisher.onPipelineChanged(new PipelineChanged(100L))

        then:
        1 * published.findById(100L) >> Optional.of(stored)
        0 * published.save(_)
        stored.configJson == json.writeValueAsString(builder.pipelineConfig(guiFull))
        stored.renderedAt.isAfter(EARLIER)
    }

    def "a changed product publishes each of its pipelines at the same time"() {
        given:
        List<PublishedPipelineConfig> saved = []
        published.findById(_) >> Optional.empty()

        when:
        publisher.onProductChanged(new ProductChanged(1L))

        then:
        1 * pipelines.findByProductId(1L) >> [guiFull, guiSecurity, apiFull]
        3 * published.save(_) >> { PublishedPipelineConfig config -> saved << config; config }
        saved*.pipelineId == [100L, 101L, 102L]
        saved*.renderedAt.unique().size() == 1
        parse(saved[2].configJson).projects.keySet() as List == ['backend-api']
    }

    def "changed global settings publish every pipeline, writing only what changed"() {
        given:
        def unchanged = stored(100L, json.writeValueAsString(builder.pipelineConfig(guiFull)))
        def outdated = stored(101L, '{}')
        published.findById(100L) >> Optional.of(unchanged)
        published.findById(101L) >> Optional.of(outdated)
        published.findById(102L) >> Optional.empty()

        when:
        publisher.onGlobalSettingsChanged(new GlobalSettingsChanged())

        then:
        1 * pipelines.findAllWithService() >> [guiFull, guiSecurity, apiFull]
        1 * published.save({ it.pipelineId == 102L })
        0 * published.save(_)
        unchanged.renderedAt == EARLIER
        outdated.renderedAt.isAfter(EARLIER)
        parse(outdated.configJson).pipeline.type == 'security'
    }

    def "every pipeline is published again at start-up"() {
        given:
        published.findById(_) >> Optional.empty()

        when:
        publisher.onStartup()

        then:
        1 * pipelines.findAllWithService() >> [guiFull, apiFull]
        1 * published.save({ it.pipelineId == 100L })
        1 * published.save({ it.pipelineId == 102L })
    }

    def "a portal without pipelines publishes nothing at start-up"() {
        when:
        publisher.onStartup()

        then:
        1 * pipelines.findAllWithService() >> []
        0 * published._
    }

    private static PublishedPipelineConfig stored(Long pipelineId, String configJson) {
        def config = new PublishedPipelineConfig(pipelineId)
        config.publish(configJson, EARLIER)
        config.markStored()
        config
    }
}
