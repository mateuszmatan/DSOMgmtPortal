package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineCommand
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.pipeline as pipelineJson
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static com.bbh.itss.dso.portal.support.Fixtures.KEY
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class PipelineControllerSpec extends Specification {

    QueryPipelinesUseCase queries = Mock()
    ManagePipelinesUseCase pipelines = Mock()
    ManagePipelineKeysUseCase keys = Mock()
    MockMvc mvc = WebMvc.of(new PipelineController(queries, pipelines, keys))

    def certScanner = product(id: 1, services: [[name: 'gui', id: 10]])
    def view = PipelineView.of(certScanner, pipeline(id: 100), null)

    def "lists a product's services with their pipelines"() {
        when:
        def response = mvc.perform(get('/api/products/1/pipelines')).andReturn().response

        then:
        1 * queries.listForProduct(1L) >> [new ServicePipelinesView(certScanner.services()[0], [view])]
        response.status == 200
        with(parse(response.contentAsString)[0]) {
            serviceName == 'gui'
            buildTool == 'GRADLE'
            pipelines[0].id == 100
            pipelines[0].activeKey.status == 'ACTIVE'
            pipelines[0].keys == null
        }
    }

    def "creating a pipeline answers 201 with the new key"() {
        when:
        def response = mvc.perform(post('/api/services/10/pipelines').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(pipelineJson()))).andReturn().response

        then:
        1 * pipelines.create(10L, { PipelineCommand c ->
            c.type() == PipelineType.FULL && c.settings().agentLabels() == ['linux-agent']
        }) >> view
        response.status == 201
        parse(response.contentAsString).activeKey.value == KEY
        parse(response.contentAsString).keys.size() == 1
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

    def "the Jenkins job #jenkinsJob is accepted as a job path or URL"() {
        when:
        def response = mvc.perform(post('/api/services/10/pipelines').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(pipelineJson(jenkinsJob: jenkinsJob)))).andReturn().response

        then:
        1 * pipelines.create(10L, { PipelineCommand c -> c.settings().jenkinsJob() == stored }) >> view
        response.status == 201

        where:
        jenkinsJob                                   || stored
        null                                         || null
        ''                                           || null
        'DevSecOps/CertScanner-gui'                  || 'DevSecOps/CertScanner-gui'
        'DevSecOps/Cert Scanner/gui'                 || 'DevSecOps/Cert Scanner/gui'
        'https://jenkins.bbh.com/job/CERT/job/gui/'  || 'https://jenkins.bbh.com/job/CERT/job/gui/'
        'http://jenkins:8080/job/gui'                || 'http://jenkins:8080/job/gui'
    }

    def "the Jenkins job #jenkinsJob is refused"() {
        when:
        def response = mvc.perform(post('/api/services/10/pipelines').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(pipelineJson(jenkinsJob: jenkinsJob)))).andReturn().response

        then:
        0 * pipelines.create(*_)
        response.status == 400
        with(parse(response.contentAsString)) {
            errors*.field == ['jenkinsJob']
            errors[0].message == message
        }

        where:
        jenkinsJob                  || message
        'ftp://jenkins.bbh.com/job' || "must be a job path such as DevSecOps/CertScanner-gui or the job's http or https URL"
        'https://jenkins bbh/job'   || "must be a job path such as DevSecOps/CertScanner-gui or the job's http or https URL"
        ' DevSecOps/gui'            || "must be a job path such as DevSecOps/CertScanner-gui or the job's http or https URL"
        'DevSecOps/gui?delay=0'     || "must be a job path such as DevSecOps/CertScanner-gui or the job's http or https URL"
        'DevSecOps/gui#main'        || "must be a job path such as DevSecOps/CertScanner-gui or the job's http or https URL"
        'a' * 1001                  || 'size must be between 0 and 1000'
    }

    def "a pipeline's Jenkins job is linked in the response"() {
        given:
        def linked = PipelineView.of(certScanner, pipeline(id: 100, jenkinsJob: 'DevSecOps/CERT/gui'), 'https://jenkins.test')

        when:
        def response = mvc.perform(get('/api/pipelines/100')).andReturn().response

        then:
        1 * queries.get(100L) >> linked
        with(parse(response.contentAsString)) {
            jenkinsJob == 'DevSecOps/CERT/gui'
            jenkinsJobUrl == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui/'
        }
    }

    def "returns, changes and deletes a pipeline"() {
        when:
        def got = mvc.perform(get('/api/pipelines/100')).andReturn().response
        def updated = mvc.perform(put('/api/pipelines/100').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(pipelineJson(description: 'Nightly')))).andReturn().response
        def deleted = mvc.perform(delete('/api/pipelines/100')).andReturn().response

        then:
        1 * queries.get(100L) >> view
        1 * pipelines.update(100L, { PipelineCommand c -> c.settings().description() == 'Nightly' }) >> view
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
        1 * keys.revokeKey(100L, 'Leaked') >> view
        missing.status == 400
        parse(missing.contentAsString).detail == 'say why the key is invalidated'
        revoked.status == 200
    }

    def "issuing a key answers the pipeline, revoking without an active key is 409"() {
        when:
        def issued = mvc.perform(post('/api/pipelines/100/keys')).andReturn().response
        def again = mvc.perform(post('/api/pipelines/100/keys/revoke').contentType(MediaType.APPLICATION_JSON)
                .content(toJson([reason: 'again']))).andReturn().response

        then:
        1 * keys.issueKey(100L) >> view
        1 * keys.revokeKey(100L, 'again') >> { throw new ConflictException('The pipeline has no active key to invalidate') }
        issued.status == 200
        again.status == 409
    }
}
