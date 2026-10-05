package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.ReadPipelineConfigUseCase
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.RenderConfigUseCase
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.dsoconfig.DsoConfigBuilder
import com.bbh.itss.dso.portal.domain.pipeline.KeyRevokedException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.test.web.servlet.MockMvc
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.UPDATED
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class DsoConfigControllerSpec extends Specification {

    static final String KEY = '6f1c2d3e-0000-4abc-9def-123456789abc'

    ReadPipelineConfigUseCase library = Mock()
    RenderConfigUseCase configs = Mock()
    MockMvc mvc = WebMvc.of(new DsoConfigController(library, configs))

    def builder = new DsoConfigBuilder(storedSettings().values())
    Product certScanner = product(id: 1L, services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])
    Map<String, Object> guiConfig = builder.pipelineConfig(certScanner, certScanner.services()[0],
            pipeline(productId: 1L, serviceId: 10L))

    def "an active key gets the pipeline config as YAML"() {
        when:
        def response = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response

        then:
        1 * library.readByKey(KEY) >> guiConfig
        response.status == 200
        response.contentType.startsWith('application/yaml')
        with(new Yaml().load(response.contentAsString) as Map) {
            pipeline.type == 'full'
            projects.gui.appId == '109f44ac-cc06-4ca0-884e-d944904f7019'
        }
    }

    def "the YAML uses block style and reads back to the same config"() {
        given:
        library.readByKey(KEY) >> guiConfig

        when:
        def yaml = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response.contentAsString

        then:
        yaml.startsWith('pipeline:\n  type: full\n  entryPoint: devSecOpsPipeline\n')
        yaml.contains('  agentNames:\n  - linux-agent\n')
        !yaml.contains('{')
        new Yaml().load(yaml) == guiConfig
    }

    def "the config is also available as JSON"() {
        when:
        def response = mvc.perform(get("/api/dso/config/$KEY").param('format', 'JSON')).andReturn().response

        then:
        1 * library.readByKey(KEY) >> guiConfig
        response.contentType.startsWith('application/json')
        parse(response.contentAsString).pipeline.projectNames == 'gui'
    }

    def "an invalidated key is 403 with the reason"() {
        given:
        def key = pipeline().revokeActiveKey('Service retired', UPDATED)

        when:
        def response = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response

        then:
        1 * library.readByKey(KEY) >> { throw new KeyRevokedException(key) }
        response.status == 403
        with(parse(response.contentAsString)) {
            status == 403
            title == 'Pipeline key invalidated'
            detail.endsWith(': Service retired')
        }
    }

    def "a key that was never issued is 404"() {
        when:
        def response = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response

        then:
        1 * library.readByKey(KEY) >> { throw new NotFoundException('Unknown DevSecOps pipeline key') }
        response.status == 404
    }

    def "the portal shows a pipeline's config by id without using its key"() {
        when:
        def response = mvc.perform(get('/api/pipelines/100/config')).andReturn().response

        then:
        1 * configs.pipelineConfig(100L) >> guiConfig
        0 * library._
        response.status == 200
        (new Yaml().load(response.contentAsString) as Map).pipeline.projectNames == 'gui'
    }

    def "the config of an unknown pipeline is 404"() {
        when:
        def response = mvc.perform(get('/api/pipelines/100/config').param('format', 'json')).andReturn().response

        then:
        1 * configs.pipelineConfig(100L) >> { throw NotFoundException.of('Pipeline', 100L) }
        response.status == 404
    }

    def "a product's config holds all of its services"() {
        given:
        configs.productConfig(1L) >> builder.productConfig(certScanner)

        when:
        def response = mvc.perform(get('/api/products/1/config')).andReturn().response

        then:
        response.status == 200
        (new Yaml().load(response.contentAsString) as Map).projects.keySet() as List == ['gui', 'backend-api']
    }

    def "the config of an unknown product is 404"() {
        given:
        configs.productConfig(1L) >> { throw NotFoundException.of('Product', 1L) }

        when:
        def response = mvc.perform(get('/api/products/1/config')).andReturn().response

        then:
        response.status == 404
    }

    def "the global part of every pipeline's config is shown as YAML or JSON"() {
        given:
        configs.settingsConfig() >> builder.globalConfig()

        when:
        def yaml = mvc.perform(get('/api/settings/config')).andReturn().response
        def json = mvc.perform(get('/api/settings/config').param('format', 'json')).andReturn().response

        then:
        yaml.status == 200
        (new Yaml().load(yaml.contentAsString) as Map).platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
        parse(json.contentAsString).defaults.releaseGate.stateFile == 'release-gate.json'
    }
}
