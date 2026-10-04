package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.common.ApiExceptionHandler
import com.bbh.itss.dso.portal.common.ConflictException
import com.bbh.itss.dso.portal.common.NotFoundException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.product as productJson
import static com.bbh.itss.dso.portal.support.ApiJson.service as serviceJson
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class ProductControllerSpec extends Specification {

    ProductCatalogService catalog = Mock()
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(catalog))
            .setControllerAdvice(new ApiExceptionHandler())
            .build()

    def "lists the products matching the search"() {
        when:
        def response = mvc.perform(get('/api/products').param('search', 'cert')).andReturn().response

        then:
        1 * catalog.list('cert') >> [new ProductSummary(1L, 'CERT', 'CertScanner', null, 'TA', 2, 3, 1, null)]
        response.status == 200
        with(parse(response.contentAsString)[0]) {
            code == 'CERT'
            serviceCount == 2
            pipelineCount == 3
            activePipelineCount == 1
        }
    }

    def "returns a product with its services"() {
        given:
        def product = product(id: 5)
        service(product, name: 'gui', id: 10)

        when:
        def response = mvc.perform(get('/api/products/5')).andReturn().response

        then:
        1 * catalog.get(5L) >> ProductResponse.from(product)
        response.status == 200
        with(parse(response.contentAsString)) {
            id == 5
            services[0].name == 'gui'
            services[0].build.tool == 'GRADLE'
            services[0].metrics.influxProject == 'CERT-gui'
            appScan.keyId == 'bbh_key-id'
        }
    }

    def "an unknown product is 404"() {
        when:
        def response = mvc.perform(get('/api/products/5')).andReturn().response

        then:
        1 * catalog.get(5L) >> { throw NotFoundException.of('Product', 5L) }
        response.status == 404
        parse(response.contentAsString).detail == 'Product 5 does not exist'
    }

    def "creating a product answers 201 with its location"() {
        given:
        def created = ProductResponse.from(product(id: 9))

        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson()))).andReturn().response

        then:
        1 * catalog.create({ ProductRequest r -> r.code() == 'CERT' && r.services()*.name() == ['gui'] }) >> created
        response.status == 201
        response.getHeader('Location') == 'http://localhost/api/products/9'
        parse(response.contentAsString).id == 9
    }

    def "an invalid request is 400 with every invalid field"() {
        given:
        def body = productJson(code: 'cert', contactEmail: 'not-an-email', services: [
                serviceJson(build: null, appScan: [applicationId: 'nope'], metrics: [influxProject: 'has space'])])

        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body))).andReturn().response

        then:
        0 * catalog.create(_)
        response.status == 400
        parse(response.contentAsString).errors*.field.sort() == ['code', 'contactEmail', 'services[0].appScan.applicationId',
                                                                 'services[0].build', 'services[0].metrics.influxProject']
    }

    def "a body that is not JSON is 400"() {
        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content('{"code": ')).andReturn().response

        then:
        response.status == 400
        parse(response.contentAsString).title == 'Malformed request'
    }

    def "updating a product returns the stored version"() {
        given:
        def updated = ProductResponse.from(product(id: 5, name: 'CertScanner 2'))

        when:
        def response = mvc.perform(put('/api/products/5').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson(name: 'CertScanner 2', version: 0)))).andReturn().response

        then:
        1 * catalog.update(5L, { ProductRequest r -> r.version() == 0L }) >> updated
        response.status == 200
        parse(response.contentAsString).name == 'CertScanner 2'
    }

    def "a conflicting update is 409"() {
        when:
        def response = mvc.perform(put('/api/products/5').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson()))).andReturn().response

        then:
        1 * catalog.update(5L, _) >> { throw new ConflictException('A product named CertScanner already exists') }
        response.status == 409
        parse(response.contentAsString).detail == 'A product named CertScanner already exists'
    }

    def "deleting a product answers 204"() {
        when:
        def response = mvc.perform(delete('/api/products/5')).andReturn().response

        then:
        1 * catalog.delete(5L)
        response.status == 204
    }
}
