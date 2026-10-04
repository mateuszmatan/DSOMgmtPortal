package com.bbh.itss.dso.portal.catalog

import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static com.bbh.itss.dso.portal.support.Fixtures.withId

class ProductSpec extends Specification {

    def "a product trims its details and stores blank ones as null"() {
        when:
        def product = new Product(new ProductDetails(' CERT ', ' CertScanner ', ' ', ' Technology Architecture ', ''), account())

        then:
        product.code == 'CERT'
        product.name == 'CertScanner'
        product.description == null
        product.ownerTeam == 'Technology Architecture'
        product.contactEmail == null
        product.appScanAccount == account()
        product.details() == new ProductDetails('CERT', 'CertScanner', null, 'Technology Architecture', null)
    }

    def "updating a product replaces its details and AppScan account"() {
        given:
        def product = product()
        def newAccount = new AppScanAccount('bbh_other', null)

        when:
        product.update(new ProductDetails('CERT2', 'CertScanner 2', 'TLS', 'TA', 'ta@bbh.com'), newAccount)

        then:
        product.details() == new ProductDetails('CERT2', 'CertScanner 2', 'TLS', 'TA', 'ta@bbh.com')
        product.appScanAccount == newAccount
    }

    def "services are listed by display order and name and found by id"() {
        given:
        def product = product()
        def gui = product.addService('gui', null, 2, settings())
        def api = withId(product.addService('backend-api', null, 1, settings()), 11L)
        def batch = product.addService('batch', null, 1, settings())

        expect:
        product.services == [api, batch, gui]
        product.service(11L).get() == api
        product.service(99L).isEmpty()
    }

    def "a service without an id yet is never found by id"() {
        given:
        def product = product()
        service(product)

        expect:
        product.service(null).isEmpty()
    }

    def "the service list cannot be changed past the product"() {
        given:
        def product = product()

        when:
        product.services.add(null)

        then:
        thrown(UnsupportedOperationException)
    }

    def "a removed service no longer belongs to the product"() {
        given:
        def certScanner = product()
        def gui = service(certScanner, name: 'gui')
        def api = service(certScanner, name: 'backend-api')
        def foreign = service(product(code: 'PAY'), name: 'gateway')

        when:
        certScanner.removeService(gui)
        certScanner.removeService(foreign)

        then:
        certScanner.services == [api]
        gui.product == null
        foreign.product.code == 'PAY'
    }

    def "a service trims its name and takes its metrics project from the product code"() {
        when:
        def service = product().addService(' backend-api ', ' ', 3, settings())

        then:
        service.name == 'backend-api'
        service.description == null
        service.displayOrder == 3
        service.metrics == new MetricsSettings(true, 'CERT-backend-api', 'test')
        service.build.tool() == BuildTool.GRADLE
        service.deployment.target() == DeployTarget.VM
        service.sonar == SonarSettings.NONE
    }

    def "an explicit metrics project is kept"() {
        when:
        def service = product().addService('gui', null, 0, settings(metrics: new MetricsSettings(true, 'cert-scanner', 'uat')))

        then:
        service.metrics == new MetricsSettings(true, 'cert-scanner', 'uat')
    }

    def "updating a service replaces all of its settings"() {
        given:
        def service = product().addService('gui', 'old', 0, settings())
        def changed = settings(sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonar:sonar'])),
                build: build(tool: BuildTool.MAVEN, sourceDir: 'gui', javaPath: '/jdk'),
                testJobs: [new TestJob(TestStage.SMOKE, null, null, 'CERT/smoke', null, null, null, null, null)],
                sshTargets: [(Region.RD): new SshTarget('rd.host', null, null, null, null)],
                urbanCodeApplications: [new UrbanCodeApplicationSettings('Cert', null, [], null, [])])

        when:
        service.update('web', 'Angular front end', 5, changed)

        then:
        service.name == 'web'
        service.description == 'Angular front end'
        service.displayOrder == 5
        service.sonar == changed.sonar()
        service.build == changed.build()
        service.settings().testJobs() == changed.testJobs()
        service.settings().sshTargets() == changed.sshTargets()
        service.settings().urbanCodeApplications() == changed.urbanCodeApplications()
        service.metrics.influxProject() == 'CERT-web'
    }

    def "sections loaded as null by JPA are restored as empty"() {
        given:
        def service = product().addService('gui', null, 0, settings())
        ['unitTests', 'tests', 'delivery', 'urbanCode', 'sonar', 'nexusIq', 'scm', 'goldenFix', 'metrics', 'flutter'].each {
            ReflectionTestUtils.setField(service, it, null)
        }

        expect:
        service.settings() == settings()
        service.sonar == SonarSettings.NONE
        service.metrics == MetricsSettings.DEFAULTS
    }

    def "a service writes its config.yaml entry with the product's AppScan account"() {
        given:
        def product = product()
        def service = product.addService('gui', null, 0, settings(
                testJobs: [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', null, null, null, null, null)]))
        def tree = new ConfigTree()

        when:
        service.writeTo(tree)

        then:
        tree.get('buildTool') == 'gradle'
        tree.get('tests.smoke.jobs') == [[name: 'smoke', job: 'CERT/gui-smoke']]
        tree.get('asoc') == [keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        tree.get('influx.project') == 'CERT-gui'
        tree.get('appId') == '109f44ac-cc06-4ca0-884e-d944904f7019'
    }

    def "a product's id is the one JPA assigned"() {
        expect:
        withId(product(), 42L).id == 42L
    }
}
