package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ProductIdentity
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ServiceIdentity
import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.DomainFixtures.account
import static com.bbh.itss.dso.portal.support.DomainFixtures.build
import static com.bbh.itss.dso.portal.support.DomainFixtures.details
import static com.bbh.itss.dso.portal.support.DomainFixtures.directory
import static com.bbh.itss.dso.portal.support.DomainFixtures.draft
import static com.bbh.itss.dso.portal.support.DomainFixtures.product
import static com.bbh.itss.dso.portal.support.DomainFixtures.service
import static com.bbh.itss.dso.portal.support.DomainFixtures.settings

class ProductSpec extends Specification {

    static final SonarSettings CERT_SONAR = SonarSettings.of(null, 'cert', ToolCommand.of(['sonarqube'], []))

    def nobody = directory()

    def "product details trim the code and the name and store blank values as null"() {
        expect:
        new ProductDetails(' CERT ', ' CertScanner ', ' ', ' Technology Architecture ', '') ==
                new ProductDetails('CERT', 'CertScanner', null, 'Technology Architecture', null)
        new ProductDetails(null, null, null, null, null).code() == null
    }

    def "a new product places its services in the requested order and names their metrics after itself"() {
        when:
        def product = Product.create(details(description: ' TLS '), account(),
                [draft(name: ' gui ', description: ' '), draft(name: 'backend-api', description: 'REST API')], nobody)

        then:
        product.id() == null
        product.version() == 0
        product.createdAt() == null
        product.updatedAt() == null
        product.code() == 'CERT'
        product.name() == 'CertScanner'
        product.description() == 'TLS'
        product.ownerTeam() == null
        product.contactEmail() == null
        product.appScanAccount() == account()
        product.services()*.name() == ['gui', 'backend-api']
        product.services()*.displayOrder() == [0, 1]
        product.services()*.description() == [null, 'REST API']
        product.services()*.id() == [null, null]
        product.services()*.settings()*.metrics()*.influxProject() == ['CERT-gui', 'CERT-backend-api']
        product.serviceIds() == [] as Set
    }

    def "an explicit metrics project is kept"() {
        when:
        def product = Product.create(details(), account(),
                [draft(metrics: new MetricsSettings(true, 'cert-scanner', 'uat'))], nobody)

        then:
        product.services()[0].settings().metrics() == new MetricsSettings(true, 'cert-scanner', 'uat')
    }

    def "a stored product lists its services by display order and name and finds them by id"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10, displayOrder: 2], [name: 'backend-api', id: 11, displayOrder: 1],
                                                [name: 'batch', id: 12, displayOrder: 1]])

        expect:
        product.id() == 5
        product.services()*.name() == ['backend-api', 'batch', 'gui']
        product.service(11L).get().name() == 'backend-api'
        product.service(99L).isEmpty()
        product.service(null).isEmpty()
        product.serviceIds() == [11L, 12L, 10L] as Set
        product.details() == details()
    }

    def "the service list cannot be changed past the product"() {
        when:
        product(services: [[name: 'gui', id: 10]]).services().add(service())

        then:
        thrown(UnsupportedOperationException)
    }

    def "a service writes its config.yaml entry with the product's AppScan account"() {
        given:
        def product = product(services: [[name: 'gui', id: 10, testJobs: [
                new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', null, null, null, null, null)]]])
        def tree = new ConfigTree()

        when:
        product.writeConfig(product.services()[0], tree)

        then:
        tree.get('buildTool') == 'gradle'
        tree.get('tests.smoke.jobs') == [[name: 'smoke', job: 'CERT/gui-smoke']]
        tree.get('asoc') == [keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        tree.get('influx.project') == 'CERT-gui'
        tree.get('appId') == '109f44ac-cc06-4ca0-884e-d944904f7019'
    }

    def "an update replaces the details and the service list: kept services change, new ones come, missing ones go"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10], [name: 'api', id: 11], [name: 'batch', id: 12]])
        def newAccount = new AppScanAccount('bbh_other', null)

        when:
        product.update(0L, details(name: 'CertScanner 2'), newAccount, [
                draft(id: 11, name: 'api', description: 'REST API'),
                draft(name: 'worker'),
                draft(id: 10, name: 'web')], nobody)

        then:
        product.id() == 5
        product.name() == 'CertScanner 2'
        product.appScanAccount() == newAccount
        product.services()*.name() == ['api', 'worker', 'web']
        product.services()*.id() == [11L, null, 10L]
        product.services()*.displayOrder() == [0, 1, 2]
        product.services()[0].description() == 'REST API'
        product.services()*.settings()*.metrics()*.influxProject() == ['CERT-api', 'CERT-worker', 'CERT-web']
    }

    def "an update that sends no version is not checked against the stored one"() {
        given:
        def product = product(version: 4, services: [[name: 'gui', id: 10]])

        when:
        product.update(null, details(), account(), [draft(id: 10, name: 'gui')], nobody)

        then:
        product.version() == 4
    }

    def "an update based on an older version is refused before anything changes"() {
        given:
        def product = product(version: 4, services: [[name: 'gui', id: 10]])

        when:
        product.update(3L, details(name: 'Other'), account(), [], nobody)

        then:
        def e = thrown(ConflictException)
        e.message == ConflictException.STALE_VERSION
        product.name() == 'CertScanner'
        product.services()*.name() == ['gui']
    }

    def "a product code used by another product is refused"() {
        when:
        Product.create(details(code: 'CERT'), account(), [draft()],
                directory(byCode: [CERT: new ProductIdentity(1, 'Certificates')]))

        then:
        def e = thrown(ConflictException)
        e.message == 'Product code CERT is already used by Certificates'
    }

    def "a product name used by another product is refused"() {
        when:
        Product.create(details(), account(), [draft()],
                directory(byName: [CertScanner: new ProductIdentity(1, 'certscanner')]))

        then:
        def e = thrown(ConflictException)
        e.message == 'A product named certscanner already exists'
    }

    def "a product keeps its own code and name on update"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10]])
        def itself = new ProductIdentity(5, 'CertScanner')

        when:
        product.update(0L, details(), account(), [draft(id: 10, name: 'gui')],
                directory(byCode: [CERT: itself], byName: [CertScanner: itself]))

        then:
        notThrown(ConflictException)
    }

    def "every invalid service is reported at once"() {
        when:
        Product.create(details(), account(), [
                draft(name: 'gui', build: build(javaPath: null)),
                draft(name: 'GUI '),
                draft(name: 'api', id: 77)], nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].build.javaPath', 'services[1].name', 'services[1].metrics.influxProject',
                              'services[2].id']
        e.problems*.message == ['set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them',
                                'another service of this product already uses this name',
                                'another service of this product writes metrics under the same project and environment',
                                'service 77 does not belong to this product']
    }

    def "metrics tags used by a service of another product are refused"() {
        given:
        def gateway = new ServiceIdentity(50, 'Payments Hub', 'gateway')

        when:
        Product.create(details(), account(), [draft()], directory(byMetrics: ['CERT-gui|test': [gateway]]))

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].metrics.influxProject']
        e.message == 'metrics project CERT-gui (test) is already used by Payments Hub / gateway'
    }

    def "a SonarQube key is unique within the product and across products"() {
        given:
        def gateway = new ServiceIdentity(50, 'Payments Hub', 'gateway')

        when:
        Product.create(details(), account(), [draft(name: 'gui', sonar: CERT_SONAR), draft(name: 'api', sonar: CERT_SONAR)],
                directory(bySonarKey: [cert: [gateway]]))

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['services[0].sonar.projectKey', 'services[1].sonar.projectKey']
        e.problems*.message == ['SonarQube project key is already used by Payments Hub / gateway',
                                'another service of this product uses this key']
    }

    def "a product's own services do not clash with themselves on update"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10, sonar: CERT_SONAR]])
        def gui = new ServiceIdentity(10, 'CertScanner', 'gui')

        when:
        product.update(0L, details(), account(), [draft(id: 10, name: 'gui', sonar: CERT_SONAR)],
                directory(byMetrics: ['CERT-gui|test': [gui]], bySonarKey: [cert: [gui]]))

        then:
        notThrown(InvalidRequestException)
    }

    def "a value used by one of the product's services and by a foreign one names the foreign one"() {
        given:
        def product = product(id: 5, services: [[name: 'gui', id: 10, sonar: CERT_SONAR]])
        def own = new ServiceIdentity(10, 'CertScanner', 'gui')
        def foreign = new ServiceIdentity(50, 'Payments Hub', 'gateway')

        when:
        product.update(0L, details(), account(), [draft(id: 10, name: 'gui', sonar: CERT_SONAR)],
                directory(bySonarKey: [cert: [own, foreign]]))

        then:
        def e = thrown(InvalidRequestException)
        e.message == 'SonarQube project key is already used by Payments Hub / gateway'
    }

    def "a product needs a code, a name, an AppScan key and named services"() {
        when:
        Product.create(new ProductDetails(' ', null, null, null, null), new AppScanAccount(' ', null),
                [draft(name: ' ')], nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['code', 'name', 'appScan.keyId', 'services[0].name']
        e.problems*.message.unique() == ['must not be blank']
    }

    def "a product without an AppScan account is refused"() {
        when:
        Product.create(details(), null, [], nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['appScan.keyId']
    }

    def "a service and a draft trim their name and store a blank description as null"() {
        expect:
        new Service(3L, ' gui ', ' ', 1, settings()) == new Service(3L, 'gui', null, 1, settings())
        new ServiceDraft(null, ' gui ', ' ', settings()) == new ServiceDraft(null, 'gui', null, settings())
        new Service(null, null, null, 0, settings()).name() == null
        new ServiceDraft(null, null, null, settings()).name() == null
        service(id: 3).hasId(3L)
        !service(id: 3).hasId(4L)
        !service(id: null).hasId(null)
    }

    def "a service and a draft cannot do without their settings"() {
        when:
        factory()

        then:
        def e = thrown(NullPointerException)
        e.message == 'a service needs its settings'

        where:
        factory << [{ new Service(1L, 'gui', null, 0, null) }, { new ServiceDraft(1L, 'gui', null, null) }]
    }

    def "a foreign service is described by its product and name"() {
        expect:
        new ServiceIdentity(50, 'Payments Hub', 'gateway').describe() == 'Payments Hub / gateway'
    }
}
