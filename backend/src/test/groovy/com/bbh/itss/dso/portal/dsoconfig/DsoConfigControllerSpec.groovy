package com.bbh.itss.dso.portal.dsoconfig

import com.bbh.itss.dso.portal.adapter.in.web.ApiExceptionHandler
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.pipeline.KeyRevokedException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.UPDATED
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class DsoConfigControllerSpec extends Specification {

    static final String KEY = '6f1c2d3e-0000-4abc-9def-123456789abc'

    ManagePipelineKeysUseCase keys = Mock()
    QueryPipelinesUseCase pipelines = Mock()
    QueryProductsUseCase products = Mock()
    GlobalSettingsRepositoryPort settings = Stub() {
        load() >> Optional.of(storedSettings())
    }
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DsoConfigController(keys, pipelines, products, new DsoConfigBuilder(settings)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build()

    Product certScanner = product(id: 1L, services: [[name: 'gui', id: 10L]])

    def "an active key gets the pipeline config as YAML"() {
        when:
        def response = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response

        then:
        1 * keys.resolveKey(KEY) >> view()
        response.status == 200
        response.contentType.startsWith('application/yaml')
        with(new Yaml().load(response.contentAsString) as Map) {
            pipeline.type == 'full'
            projects.gui.appId == '109f44ac-cc06-4ca0-884e-d944904f7019'
        }
    }

    def "the config is also available as JSON"() {
        when:
        def response = mvc.perform(get("/api/dso/config/$KEY").param('format', 'JSON')).andReturn().response

        then:
        1 * keys.resolveKey(KEY) >> view()
        response.contentType.startsWith('application/json')
        parse(response.contentAsString).pipeline.projectNames == 'gui'
    }

    def "an invalidated key is 403 with the reason"() {
        given:
        def key = pipeline().revokeActiveKey('Service retired', UPDATED)

        when:
        def response = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response

        then:
        1 * keys.resolveKey(KEY) >> { throw new KeyRevokedException(key) }
        response.status == 403
        with(parse(response.contentAsString)) {
            title == 'Pipeline key invalidated'
            detail.endsWith(': Service retired')
        }
    }

    def "a key that was never issued is 404"() {
        when:
        def response = mvc.perform(get("/api/dso/config/$KEY")).andReturn().response

        then:
        1 * keys.resolveKey(KEY) >> { throw new NotFoundException('Unknown DevSecOps pipeline key') }
        response.status == 404
    }

    def "the portal shows a pipeline's config by id without using its key"() {
        when:
        def response = mvc.perform(get('/api/pipelines/100/config')).andReturn().response

        then:
        1 * pipelines.get(100L) >> view()
        0 * keys.resolveKey(_)
        response.status == 200
        (new Yaml().load(response.contentAsString) as Map).pipeline.projectNames == 'gui'
    }

    def "the config of an unknown pipeline is 404"() {
        when:
        def response = mvc.perform(get('/api/pipelines/100/config').param('format', 'json')).andReturn().response

        then:
        1 * pipelines.get(100L) >> { throw NotFoundException.of('Pipeline', 100L) }
        response.status == 404
    }

    def "a product's config holds all of its services"() {
        given:
        products.get(1L) >> product(id: 1L, services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])

        when:
        def response = mvc.perform(get('/api/products/1/config')).andReturn().response

        then:
        response.status == 200
        (new Yaml().load(response.contentAsString) as Map).projects.keySet() as List == ['gui', 'backend-api']
    }

    def "the config of an unknown product is 404"() {
        given:
        products.get(1L) >> { throw NotFoundException.of('Product', 1L) }

        when:
        def response = mvc.perform(get('/api/products/1/config')).andReturn().response

        then:
        response.status == 404
    }

    def "the global part of every pipeline's config is shown as YAML or JSON"() {
        when:
        def yaml = mvc.perform(get('/api/settings/config')).andReturn().response
        def json = mvc.perform(get('/api/settings/config').param('format', 'json')).andReturn().response

        then:
        yaml.status == 200
        (new Yaml().load(yaml.contentAsString) as Map).platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
        parse(json.contentAsString).defaults.releaseGate.stateFile == 'release-gate.json'
    }

    private PipelineView view() {
        PipelineView.of(certScanner, pipeline(productId: 1L, serviceId: 10L), 'https://jenkins.test')
    }
}
