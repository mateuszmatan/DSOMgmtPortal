package com.bbh.itss.dso.portal.pipeline

import com.bbh.itss.dso.portal.common.ApiExceptionHandler
import com.bbh.itss.dso.portal.common.ConflictException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.pipeline as pipelineJson
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class PipelineControllerSpec extends Specification {

    PipelineService pipelines = Mock()
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new PipelineController(pipelines))
            .setControllerAdvice(new ApiExceptionHandler())
            .build()

    def gui = service(product(id: 1), name: 'gui', id: 10)
    def pipeline = pipeline(gui, id: 100)

    def "lists a product's services with their pipelines"() {
        when:
        def response = mvc.perform(get('/api/products/1/pipelines')).andReturn().response

        then:
        1 * pipelines.listForProduct(1L) >> [ServicePipelines.of(gui, [PipelineResponse.summary(pipeline)])]
        response.status == 200
        with(parse(response.contentAsString)[0]) {
            serviceName == 'gui'
            buildTool == 'GRADLE'
            pipelines[0].id == 100
            pipelines[0].activeKey.status == 'ACTIVE'
        }
    }

    def "creating a pipeline answers 201 with the new key"() {
        when:
        def response = mvc.perform(post('/api/services/10/pipelines').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(pipelineJson()))).andReturn().response

        then:
        1 * pipelines.create(10L, { PipelineRequest r -> r.type() == PipelineType.FULL && r.agentLabels() == ['linux-agent'] }) >>
                PipelineResponse.withKeys(pipeline)
        response.status == 201
        parse(response.contentAsString).activeKey.value == pipeline.activeKey().get().value
    }

    def "a pipeline needs a type and valid agent labels"() {
        when:
        def response = mvc.perform(post('/api/services/10/pipelines').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body))).andReturn().response

        then:
        0 * pipelines.create(*_)
        response.status == 400
        parse(response.contentAsString).errors*.field == fields

        where:
        body                                      || fields
        pipelineJson(type: null)                  || ['type']
        pipelineJson(agentLabels: [])             || ['agentLabels']
        pipelineJson(agentLabels: ['linux node']) || ['agentLabels[0]']
    }

    def "returns, changes and deletes a pipeline"() {
        when:
        def got = mvc.perform(get('/api/pipelines/100')).andReturn().response
        def updated = mvc.perform(put('/api/pipelines/100').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(pipelineJson(description: 'Nightly')))).andReturn().response
        def deleted = mvc.perform(delete('/api/pipelines/100')).andReturn().response

        then:
        1 * pipelines.get(100L) >> PipelineResponse.withKeys(pipeline)
        1 * pipelines.update(100L, { it.description() == 'Nightly' }) >> PipelineResponse.withKeys(pipeline)
        1 * pipelines.delete(100L)
        got.status == 200
        parse(got.contentAsString).keys.size() == 1
        updated.status == 200
        deleted.status == 204
    }

    def "revoking a key needs a reason"() {
        when:
        def missing = mvc.perform(post('/api/pipelines/100/keys/revoke').contentType(MediaType.APPLICATION_JSON)
                .content(toJson([reason: ' ']))).andReturn().response
        def revoked = mvc.perform(post('/api/pipelines/100/keys/revoke').contentType(MediaType.APPLICATION_JSON)
                .content(toJson([reason: 'Leaked']))).andReturn().response

        then:
        1 * pipelines.revokeKey(100L, 'Leaked') >> PipelineResponse.withKeys(pipeline)
        missing.status == 400
        parse(missing.contentAsString).detail == 'say why the key is invalidated'
        revoked.status == 200
    }

    def "issuing a key without an active one to replace still works, revoking twice is 409"() {
        when:
        def issued = mvc.perform(post('/api/pipelines/100/keys')).andReturn().response
        def again = mvc.perform(post('/api/pipelines/100/keys/revoke').contentType(MediaType.APPLICATION_JSON)
                .content(toJson([reason: 'again']))).andReturn().response

        then:
        1 * pipelines.issueKey(100L) >> PipelineResponse.withKeys(pipeline)
        1 * pipelines.revokeKey(100L, 'again') >> { throw new ConflictException('The pipeline has no active key to invalidate') }
        issued.status == 200
        again.status == 409
    }
}
