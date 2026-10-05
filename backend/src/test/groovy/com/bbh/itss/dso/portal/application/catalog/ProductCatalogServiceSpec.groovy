package com.bbh.itss.dso.portal.application.catalog

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand
import com.bbh.itss.dso.portal.application.catalog.port.in.ServiceCommand
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductSummary
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.settings

class ProductCatalogServiceSpec extends Specification {

    static final SonarSettings CERT_SONAR = SonarSettings.of(null, 'cert', command(['sonarqube']))
    static final Instant CHANGED = Instant.parse('2026-10-03T10:00:00Z')

    ProductRepositoryPort products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineCountsPort pipelineCounts = Stub()
    PublishPipelineConfigsUseCase publisher = Mock()

    @Subject
    def catalog = new ProductCatalogService(products, pipelineCounts, publisher)

    def "the list shows each product with its service and pipeline counts"() {
        given:
        products.summaries() >> [summary(1, 'CERT', 'CertScanner', 'TA', null), summary(2, 'PAY', 'Payments Hub', null, null)]
        products.servicesPerProduct() >> [1L: 2L, 2L: 4L]
        pipelineCounts.pipelinesPerProduct() >> [1L: 3L]
        pipelineCounts.activePipelinesPerProduct() >> [1L: 1L]

        when:
        def list = catalog.list(null)

        then:
        list*.code() == ['CERT', 'PAY']
        list*.serviceCount() == [2, 4]
        list*.pipelineCount() == [3, 0]
        list*.activePipelineCount() == [1, 0]
        list[0].ownerTeam() == 'TA'
        list[0].id() == 1
        list[0].name() == 'CertScanner'
        list[0].updatedAt() == CHANGED
    }

    def "searching '#search' finds #codes"() {
        given:
        products.summaries() >> [summary(1, 'CERT', 'CertScanner', 'Technology Architecture', null),
                                 summary(2, 'PAY', 'Payments Hub', null, 'Payment orchestration')]

        expect:
        catalog.list(search)*.code() == codes

        where:
        search          || codes
        null            || ['CERT', 'PAY']
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
        products.load(5L) >> Optional.of(product(id: 5, services: [[name: 'gui', id: 10]]))

        when:
        def product = catalog.get(5L)

        then:
        product.id() == 5
        product.services()*.name() == ['gui']
        product.services()[0].settings().metrics().influxProject() == 'CERT-gui'
    }

    def "an unknown product is not found"() {
        when:
        catalog.get(5L)

        then:
        def e = thrown(NotFoundException)
        e.message == 'Product 5 does not exist'
    }

    def "a new product is stored with its services in the requested order and its pipelines are published"() {
        given:
        def command = command(services: [service(name: 'gui'), service(name: 'backend-api')])

        when:
        def created = catalog.create(command)

        then:
        1 * products.save({ Product p -> p.id() == null && p.services()*.displayOrder() == [0, 1] }) >> { Product p ->
            stored(9L, p)
        }

        then:
        1 * publisher.productChanged(9L)
        created.id() == 9
        created.services()*.name() == ['gui', 'backend-api']
        created.services()*.settings()*.metrics()*.influxProject() == ['CERT-gui', 'CERT-backend-api']
    }

    def "the repository answers who already uses a product code"() {
        given:
        products.findProductByCode('CERT') >> Optional.of(new ProductDirectory.ProductIdentity(1L, 'Certificates'))

        when:
        catalog.create(command())

        then:
        def e = thrown(ConflictException)
        e.message == 'Product code CERT is already used by Certificates'
        0 * products.save(_)
        0 * publisher._
    }

    def "every invalid service is reported at once and nothing is stored"() {
        given:
        products.findServicesBySonarProjectKey('cert') >> [new ProductDirectory.ServiceIdentity(50L, 'Payments Hub', 'gateway')]
        def command = command(services: [
                service(name: 'gui', settings: settings(build: build(javaPath: null))),
                service(name: 'gui'),
                service(name: 'api', id: 77L),
                service(name: 'batch', settings: settings(sonar: CERT_SONAR))])

        when:
        catalog.create(command)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].build.javaPath', 'services[1].name', 'services[1].metrics.influxProject',
                              'services[2].id', 'services[3].sonar.projectKey']
        e.problems*.message[4] == 'SonarQube project key is already used by Payments Hub / gateway'
        0 * products.save(_)
    }

    def "an update replaces the details and the service list and publishes the product"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5, services: [[name: 'gui', id: 10], [name: 'api', id: 11],
                                                                   [name: 'batch', id: 12]]))

        when:
        def updated = catalog.update(5L, command(version: 0L, name: 'CertScanner 2', services: [
                service(id: 11L, name: 'api', description: 'REST API'), service(name: 'worker'),
                service(id: 10L, name: 'web')]))

        then:
        1 * products.save({ Product p ->
            p.name() == 'CertScanner 2' && p.services()*.name() == ['api', 'worker', 'web'] &&
                    p.services()*.id() == [11L, null, 10L] && p.services()*.displayOrder() == [0, 1, 2]
        }) >> { Product p -> p }

        then:
        1 * publisher.productChanged(5L)
        updated.services()[0].description() == 'REST API'
    }

    def "an update based on an older version is refused"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5, version: 1))

        when:
        catalog.update(5L, command(version: 3L))

        then:
        def e = thrown(ConflictException)
        e.message == ConflictException.STALE_VERSION
        0 * products.save(_)
        0 * publisher._
    }

    def "updating an unknown product fails"() {
        when:
        catalog.update(5L, command())

        then:
        thrown(NotFoundException)
        0 * products.save(_)
    }

    def "a product is deleted with everything it owns"() {
        given:
        products.load(5L) >> Optional.of(product(id: 5))

        when:
        catalog.delete(5L)

        then:
        1 * products.delete(5L)
    }

    def "deleting an unknown product fails"() {
        when:
        catalog.delete(5L)

        then:
        thrown(NotFoundException)
        0 * products.delete(_)
    }

    private static ProductSummary summary(long id, String code, String name, String ownerTeam, String description) {
        new ProductSummary(id, code, name, description, ownerTeam, CHANGED)
    }

    private static ServiceCommand service(Map args) {
        new ServiceCommand(args.id as Long, args.name as String, args.description as String,
                args.settings ?: settings())
    }

    private static ProductCommand command(Map args = [:]) {
        new ProductCommand(args.version as Long, details(args), account(),
                args.services as List<ServiceCommand> ?: [service(name: 'gui')])
    }

    private static Product stored(long id, Product product) {
        Product.restore(id, product.details(), product.appScanAccount(), product.services(), 0, CHANGED, CHANGED)
    }
}
