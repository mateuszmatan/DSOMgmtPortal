package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.common.ConflictException
import com.bbh.itss.dso.portal.common.InvalidRequestException
import com.bbh.itss.dso.portal.common.NotFoundException
import org.spockframework.mock.EmptyOrDummyResponse
import org.springframework.orm.ObjectOptimisticLockingFailureException
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.productRequest
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static com.bbh.itss.dso.portal.support.Fixtures.serviceRequest
import static com.bbh.itss.dso.portal.support.Fixtures.withId

class ProductCatalogServiceSpec extends Specification {

    ProductRepository products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ServiceDefinitionRepository services = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineStatistics statistics = Stub()

    @Subject
    def catalog = new ProductCatalogService(products, services, statistics)

    def "the list shows each product with its service and pipeline counts"() {
        given:
        products.findAllByOrderByNameAsc() >> [product(id: 1, code: 'CERT', name: 'CertScanner', ownerTeam: 'TA'),
                                               product(id: 2, code: 'PAY', name: 'Payments Hub')]
        services.countByProduct() >> [[1L, 2L] as Object[], [2, 4] as Object[]]
        statistics.pipelinesPerProduct() >> [1L: 3L]
        statistics.activePipelinesPerProduct() >> [1L: 1L]

        when:
        def list = catalog.list(null)

        then:
        list*.code == ['CERT', 'PAY']
        list*.serviceCount == [2, 4]
        list*.pipelineCount == [3, 0]
        list*.activePipelineCount == [1, 0]
        list[0].ownerTeam == 'TA'
    }

    def "searching '#search' finds #codes"() {
        given:
        products.findAllByOrderByNameAsc() >> [
                product(id: 1, code: 'CERT', name: 'CertScanner', ownerTeam: 'Technology Architecture'),
                product(id: 2, code: 'PAY', name: 'Payments Hub', description: 'Payment orchestration')]

        expect:
        catalog.list(search)*.code == codes

        where:
        search          || codes
        ''              || ['CERT', 'PAY']
        '  '            || ['CERT', 'PAY']
        ' cert '        || ['CERT']
        'pay'           || ['PAY']
        'ARCHITECTURE'  || ['CERT']
        'orchestration' || ['PAY']
        'nothing'       || []
    }

    def "a product is returned with its services"() {
        given:
        def product = product(id: 5)
        service(product, name: 'gui', id: 10)
        products.findById(5L) >> Optional.of(product)

        when:
        def response = catalog.get(5L)

        then:
        response.id == 5
        response.code == 'CERT'
        response.services*.name == ['gui']
        response.services[0].metrics.influxProject() == 'CERT-gui'
    }

    def "an unknown product is not found"() {
        when:
        catalog.get(5L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Product 5 does not exist'
    }

    def "a new product is stored with its services in the requested order"() {
        given:
        def request = productRequest(services: [serviceRequest(name: 'gui'), serviceRequest(name: 'backend-api')])

        when:
        def response = catalog.create(request)

        then:
        1 * products.saveAndFlush({ Product p -> p.services*.displayOrder == [0, 1] }) >> { Product p -> withId(p, 9L) }
        response.id == 9
        response.services*.name == ['gui', 'backend-api']
        response.services*.metrics*.influxProject() == ['CERT-gui', 'CERT-backend-api']
    }

    def "a product code used by another product is refused"() {
        given:
        products.findByCodeIgnoreCase('CERT') >> Optional.of(product(id: 1, name: 'Certificates'))

        when:
        catalog.create(productRequest(code: 'CERT'))

        then:
        def e = thrown(ConflictException)
        e.message == 'Product code CERT is already used by Certificates'
        0 * products.saveAndFlush(_)
    }

    def "a product name used by another product is refused"() {
        given:
        products.findByNameIgnoreCase('CertScanner') >> Optional.of(product(id: 1, name: 'certscanner'))

        when:
        catalog.create(productRequest())

        then:
        def e = thrown(ConflictException)
        e.message == 'A product named certscanner already exists'
    }

    def "every invalid service is reported at once"() {
        given:
        def request = productRequest(services: [
                serviceRequest(name: 'gui', build: build(javaPath: null)),
                serviceRequest(name: 'gui'),
                serviceRequest(name: 'api', id: 77)])

        when:
        catalog.create(request)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].build.javaPath', 'services[1].name', 'services[1].metrics.influxProject',
                              'services[2].id']
        e.problems.find { it.field == 'services[2].id' }.message == 'service 77 does not belong to this product'
        0 * products.saveAndFlush(_)
    }

    def "metrics tags used by a service of another product are refused"() {
        given:
        def other = service(product(id: 2, name: 'Payments Hub'), name: 'gateway', id: 50)
        services.findByMetricsTags('CERT-gui', 'test') >> Optional.of(other)

        when:
        catalog.create(productRequest())

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].metrics.influxProject']
        e.message == 'metrics project CERT-gui (test) is already used by Payments Hub / gateway'
    }

    def "a SonarQube key is unique within the product and across products"() {
        given:
        def other = service(product(id: 2, name: 'Payments Hub'), name: 'gateway', id: 50)
        services.findBySonarProjectKey('cert') >> Optional.of(other)
        def request = productRequest(services: [
                serviceRequest(name: 'gui', sonar: new SonarSettings(null, 'cert')),
                serviceRequest(name: 'api', sonar: new SonarSettings(null, 'cert'))])

        when:
        catalog.create(request)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].sonar.projectKey', 'services[1].sonar.projectKey']
        e.problems*.message == ['SonarQube project key is already used by Payments Hub / gateway',
                                'another service of this product uses this key']
    }

    def "a product's own services do not clash with themselves on update"() {
        given:
        def product = product(id: 5)
        def gui = service(product, name: 'gui', id: 10, sonar: new SonarSettings(null, 'cert'))
        products.findById(5L) >> Optional.of(product)
        products.findByCodeIgnoreCase('CERT') >> Optional.of(product)
        products.findByNameIgnoreCase('CertScanner') >> Optional.of(product)
        services.findBySonarProjectKey('cert') >> Optional.of(gui)
        services.findByMetricsTags('CERT-gui', 'test') >> Optional.of(gui)

        when:
        catalog.update(5L, productRequest(services: [serviceRequest(id: 10, name: 'gui', sonar: new SonarSettings(null, 'cert'))]))

        then:
        1 * products.saveAndFlush(product) >> product
        notThrown(InvalidRequestException)
    }

    def "an update based on an older version is refused"() {
        given:
        products.findById(5L) >> Optional.of(product(id: 5))

        when:
        catalog.update(5L, productRequest(version: 3L))

        then:
        thrown(ObjectOptimisticLockingFailureException)
        0 * products.saveAndFlush(_)
    }

    def "an update replaces the service list: kept services change, new ones are added, missing ones removed"() {
        given:
        def product = product(id: 5)
        def gui = service(product, name: 'gui', id: 10)
        def api = service(product, name: 'api', id: 11)
        def batch = service(product, name: 'batch', id: 12)
        products.findById(5L) >> Optional.of(product)

        when:
        catalog.update(5L, productRequest(version: 0L, name: 'CertScanner 2', services: [
                serviceRequest(id: 11, name: 'api', description: 'REST API'),
                serviceRequest(name: 'worker'),
                serviceRequest(id: 10, name: 'web')]))

        then:
        1 * products.flush()

        then:
        1 * products.saveAndFlush(product) >> product
        product.name == 'CertScanner 2'
        product.services*.name == ['api', 'worker', 'web']
        product.services*.displayOrder == [0, 1, 2]
        gui.name == 'web'
        api.description == 'REST API'
        batch.product == null
    }

    def "a product is deleted with everything it owns"() {
        given:
        def product = product(id: 5)
        products.findById(5L) >> Optional.of(product)

        when:
        catalog.delete(5L)

        then:
        1 * products.delete(product)
    }

    def "deleting an unknown product fails"() {
        when:
        catalog.delete(5L)

        then:
        thrown(NotFoundException)
        0 * products.delete(_)
    }

    def "count rows of any number type become a map"() {
        expect:
        ProductCatalogService.toCountMap([[1, 2L] as Object[], [3L, 4] as Object[]]) == [1L: 2L, 3L: 4L]
    }
}
