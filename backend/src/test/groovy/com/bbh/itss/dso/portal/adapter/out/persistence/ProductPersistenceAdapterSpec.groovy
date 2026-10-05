package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ProductIdentity
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ServiceIdentity
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.fullSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:product-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import([ProductPersistenceAdapter, ProductMapper])
class ProductPersistenceAdapterSpec extends Specification {

    @Subject
    @Autowired
    ProductPersistenceAdapter adapter

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    def "a product with every section of every service is stored and read back unchanged"() {
        given:
        def created = Product.create(details(description: 'Scans certificates', ownerTeam: 'TA',
                contactEmail: 'ta@bbh.com'), account(), [new ServiceDraft(null, 'gui', 'Angular', fullSettings('gui')),
                                                         new ServiceDraft(null, 'api', null, settings())], adapter)

        when:
        def saved = adapter.save(created)
        entities.clear()
        def loaded = adapter.load(saved.id()).get()

        then:
        saved.id() != null
        saved.version() == 0
        saved.createdAt() != null
        saved.updatedAt() == saved.createdAt()
        loaded.details() == created.details()
        loaded.appScanAccount() == account()
        loaded.services()*.id() == saved.services()*.id()
        loaded.services()*.name() == ['gui', 'api']
        loaded.services()*.description() == ['Angular', null]
        loaded.services()*.displayOrder() == [0, 1]
        loaded.services()[0].settings() == fullSettings('gui')
        loaded.services()[1].settings() == settings(metrics: new MetricsSettings(true, 'CERT-api', 'test'))
        loaded.services()[0].settings().sshTargets().keySet() as List == [RD, QC]
        loaded.version() == saved.version()
        loaded.createdAt() == saved.createdAt()
    }

    def "the columns hold the values the library view and the old rows use"() {
        when:
        def saved = adapter.save(Product.create(details(), account(), [new ServiceDraft(null, 'gui', null,
                fullSettings('gui'))], adapter))
        def serviceId = saved.services()[0].id()

        then:
        jdbc.queryForMap('SELECT CODE, NAME, ASOC_KEY_ID, ASOC_SECRET_CREDENTIALS_ID FROM DSO_PRODUCT') ==
                [CODE: 'CERT', NAME: 'CertScanner', ASOC_KEY_ID: 'bbh_key-id',
                 ASOC_SECRET_CREDENTIALS_ID: 'hcl-app-scan-account']
        jdbc.queryForMap('''SELECT BUILD_TOOL, BUILD_TASKS, DELIVERY_TASKS, BITBUCKET_REVIEWERS, NEXUS_IQ_SCAN_PATTERNS,
                GOLDEN_FIX_ENABLED, INFLUX_PROJECT FROM DSO_SERVICE WHERE ID = ?''', serviceId) ==
                [BUILD_TOOL: 'MAVEN', BUILD_TASKS: 'build-task\nsecond', DELIVERY_TASKS: 'delivery-task\nsecond',
                 BITBUCKET_REVIEWERS: 'alice,bob', NEXUS_IQ_SCAN_PATTERNS: '**/*.war\n**/*.jar', GOLDEN_FIX_ENABLED: 0,
                 INFLUX_PROJECT: 'cert-gui']
        jdbc.queryForList('SELECT POSITION, STAGE FROM DSO_SERVICE_TEST_JOB WHERE SERVICE_ID = ? ORDER BY POSITION',
                serviceId)*.STAGE == ['SMOKE', 'REGRESSION', 'PERFORMANCE']
        jdbc.queryForList('SELECT POSITION, APPLICATION_NAME FROM DSO_UCD_APPLICATION WHERE SERVICE_ID = ? ORDER BY POSITION',
                serviceId)*.APPLICATION_NAME == ['Cert', 'Cert Batch']
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_UCD_COMPONENT', Integer) == 2
        jdbc.queryForList('SELECT REGION FROM DSO_SERVICE_SSH_TARGET WHERE SERVICE_ID = ? ORDER BY REGION',
                serviceId)*.REGION == ['QC', 'RD']
        jdbc.queryForList('SELECT REGION FROM DSO_SERVICE_OPENSHIFT_TARGET WHERE SERVICE_ID = ? ORDER BY REGION',
                serviceId)*.REGION == ['QC', 'RD']
    }

    def "an update replaces the details and the service list, keeping the ids of the services it keeps"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui'), draft('api'), draft('batch')],
                adapter))
        def (gui, api, batch) = stored.services()*.id()

        when:
        stored.update(stored.version(), details(name: 'CertScanner 2', ownerTeam: 'Architecture'), account(),
                [new ServiceDraft(api, 'api', 'REST API', settings()), new ServiceDraft(null, 'batch', null, settings()),
                 new ServiceDraft(gui, 'web', null, settings())], adapter)
        def saved = adapter.save(stored)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        saved.version() > stored.version()
        loaded.version() == saved.version()
        loaded.name() == 'CertScanner 2'
        loaded.ownerTeam() == 'Architecture'
        loaded.services()*.name() == ['api', 'batch', 'web']
        loaded.services()*.displayOrder() == [0, 1, 2]
        loaded.services()[0].id() == api
        loaded.services()[0].description() == 'REST API'
        loaded.services()[1].id() !in [gui, api, batch]
        loaded.services()[2].id() == gui
        loaded.services()[2].settings().metrics().influxProject() == 'CERT-web'
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE', Integer) == 3
    }

    def "changed lists of a kept service replace the stored rows and empty lists remove them"() {
        given:
        def created = adapter.save(Product.create(details(), account(), [new ServiceDraft(null, 'gui', null,
                fullSettings('gui'))], adapter))
        entities.clear()
        def stored = adapter.load(created.id()).get()
        def gui = stored.services()[0].id()
        def changed = fullSettings('gui')
        def trimmed = new ServiceSettings(changed.build(), changed.unitTests(),
                changed.tests(), changed.testJobs().take(1), changed.deployment(), changed.delivery(),
                changed.urbanCode(), changed.urbanCodeApplications().drop(1), [(QC): changed.sshTargets()[QC]], [:],
                changed.appScan(), changed.sonar(), changed.nexusIq(), changed.scm(), changed.goldenFix(),
                changed.metrics(), changed.flutter())

        when:
        stored.update(null, details(), account(), [new ServiceDraft(gui, 'gui', null, trimmed)], adapter)
        adapter.save(stored)
        entities.clear()

        then:
        adapter.load(stored.id()).get().services()[0].settings() == trimmed
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE_TEST_JOB', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_UCD_APPLICATION', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_UCD_COMPONENT', Integer) == 0
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE_SSH_TARGET', Integer) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_SERVICE_OPENSHIFT_TARGET', Integer) == 0
    }

    def "a new service may take the name of a service removed in the same save"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui'), draft('api')], adapter))

        when:
        stored.update(stored.version(), details(), account(), [draft('api', stored.services()[1].id()), draft('gui')],
                adapter)
        def saved = adapter.save(stored)

        then:
        saved.services()*.name() == ['api', 'gui']
        saved.services()[1].id() != stored.services().find { it.name() == 'gui' }?.id()
        jdbc.queryForList('SELECT NAME FROM DSO_SERVICE ORDER BY DISPLAY_ORDER')*.NAME == ['api', 'gui']
    }

    def "a product read at another version than the stored one is not written"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui')], adapter))
        def stale = Product.restore(stored.id(), details(name: 'Stale'), account(), stored.services(),
                stored.version() + 3, stored.createdAt(), stored.updatedAt())

        when:
        adapter.save(stale)

        then:
        def e = thrown(ConflictException)
        e.message == ConflictException.STALE_VERSION
        jdbc.queryForObject('SELECT NAME FROM DSO_PRODUCT', String) == 'CertScanner'
    }

    def "a product deleted in the meantime cannot be written"() {
        given:
        def gone = Product.restore(404L, details(), account(), [], 0, null, null)

        when:
        adapter.save(gone)

        then:
        def e = thrown(ConflictException)
        e.message == ConflictException.STALE_VERSION
    }

    def "products are listed by name with their summaries and service counts"() {
        given:
        def payments = adapter.save(Product.create(details(code: 'PAY', name: 'Payments Hub', description: 'Payments'),
                account(), [draft('gateway'), draft('ledger'), draft('mobile')], adapter))
        def cert = adapter.save(Product.create(details(ownerTeam: 'TA'), account(), [draft('gui')], adapter))
        def empty = adapter.save(Product.create(details(code: 'EMPTY', name: 'Empty'), account(), [], adapter))
        entities.clear()

        expect:
        adapter.findAll()*.code() == ['CERT', 'EMPTY', 'PAY']
        adapter.findAll()[2].services()*.name() == ['gateway', 'ledger', 'mobile']
        adapter.summaries()*.code() == ['CERT', 'EMPTY', 'PAY']
        with(adapter.summaries()[0]) {
            id() == cert.id()
            name() == 'CertScanner'
            ownerTeam() == 'TA'
            description() == null
            updatedAt() == cert.updatedAt()
        }
        adapter.summaries()[2].description() == 'Payments'
        adapter.servicesPerProduct() == [(payments.id()): 3L, (cert.id()): 1L]
        !adapter.servicesPerProduct().containsKey(empty.id())
    }

    def "a product is found through any of its services"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [draft('gui'), draft('api')], adapter))
        entities.clear()

        expect:
        adapter.findByServiceId(stored.services()[1].id()).get().id() == stored.id()
        adapter.findByServiceId(stored.services()[1].id()).get().services()*.name() == ['gui', 'api']
        adapter.findByServiceId(9999L) == Optional.empty()
        adapter.load(9999L) == Optional.empty()
    }

    def "the directory finds products by code and name and services by their metrics tags and SonarQube key"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [
                draft('gui', null, settings(sonar: SonarSettings.of(null, 'cert-gui', command(['sonarqube'])))),
                draft('api', null, settings(metrics: new MetricsSettings(true, 'Cert-API', 'QC')))], adapter))
        def (gui, api) = stored.services()*.id()
        entities.clear()

        expect:
        adapter.findProductByCode('cert') == Optional.of(new ProductIdentity(stored.id(), 'CertScanner'))
        adapter.findProductByName('CERTSCANNER') == Optional.of(new ProductIdentity(stored.id(), 'CertScanner'))
        adapter.findProductByCode('PAY') == Optional.empty()
        adapter.findProductByName('Payments') == Optional.empty()
        adapter.findServicesByMetricsTags('cert-api', 'qc') == [new ServiceIdentity(api, 'CertScanner', 'api')]
        adapter.findServicesByMetricsTags('CERT-gui', 'test') == [new ServiceIdentity(gui, 'CertScanner', 'gui')]
        adapter.findServicesByMetricsTags('cert-api', 'test') == []
        adapter.findServicesBySonarProjectKey('cert-gui') == [new ServiceIdentity(gui, 'CertScanner', 'gui')]
        adapter.findServicesBySonarProjectKey('CERT-GUI') == []
    }

    def "deleting a product removes its services and their rows"() {
        given:
        def stored = adapter.save(Product.create(details(), account(), [new ServiceDraft(null, 'gui', null,
                fullSettings('gui'))], adapter))
        entities.clear()

        when:
        adapter.delete(stored.id())
        adapter.delete(9999L)
        entities.flush()

        then:
        adapter.load(stored.id()) == Optional.empty()
        ['DSO_PRODUCT', 'DSO_SERVICE', 'DSO_SERVICE_TEST_JOB', 'DSO_UCD_APPLICATION', 'DSO_UCD_COMPONENT',
         'DSO_SERVICE_SSH_TARGET', 'DSO_SERVICE_OPENSHIFT_TARGET'].every {
            jdbc.queryForObject("SELECT COUNT(*) FROM $it" as String, Integer) == 0
        }
    }

    private static ServiceDraft draft(String name, Long id = null, ServiceSettings settings = settings()) {
        new ServiceDraft(id, name, null, settings)
    }
}
