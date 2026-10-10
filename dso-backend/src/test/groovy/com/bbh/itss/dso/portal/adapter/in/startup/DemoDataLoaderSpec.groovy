package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DemoDataLoaderSpec extends Specification {

    static final List<String> DEPARTMENTS = ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody',
                                             'Fund Services']
    static final List<String> CODES = ['CERTSCANNER', 'DOCSENSE', 'ADVISORAI', 'DEALFLOW', 'LPPORTAL', 'ACCESSHUB',
                                       'SAFEKEEP', 'CORPACT', 'PAYHUB', 'NAVCALC']

    ProductsUseCase products = Mock()
    DepartmentsUseCase departments = Mock()
    PipelinesUseCase pipelines = Mock()
    ManageGlobalSettingsUseCase settings = Mock()
    List<DepartmentView> known = DEPARTMENTS.withIndex().collect { name, int index -> department(index + 1, name) }
    List<ProductCommand> created = []
    List<Map> requested = []
    List<List<String>> revoked = []
    List<Product> stored = []

    def loader = new DemoDataLoader(products, departments, pipelines, settings)

    def "a database holding #codes is left alone"() {
        when:
        loader.run(null)

        then:
        1 * products.list(null) >> codes.withIndex().collect { code, int index -> summary(index + 1, code) }
        0 * settings.update(*_)
        0 * products.create(_)
        0 * departments._
        0 * pipelines._

        where:
        codes << [['CERT'], ['CERTSCANNER', 'CERT'], CODES]
    }

    def "an empty database gets ten products in five departments and a Jenkins to link unless one is set: #jenkinsUrl"() {
        given:
        demoCatalog(jenkinsUrl)

        when:
        loader.run(null)

        then:
        updates * settings.update(null, { it.platform().jenkinsUrl() == 'https://jenkins.bbh.com' })
        0 * departments.create(_)
        created*.details()*.code() == CODES
        created*.details()*.departmentId() == [3L, 1L, 1L, 2L, 2L, 3L, 4L, 4L, 5L, 5L]
        created*.services()*.size() == [2, 2, 2, 2, 3, 2, 2, 2, 4, 2]
        created.every { it.services().every { service -> problems(service.settings()) == [] } }
        created.drop(1)*.appScan()*.keyId().every { it.startsWith('bbh_') }
        requested.size() == 49
        requested.count { it.action == 'update' } == 23
        requested.countBy { it.type } == [(FULL): 23, (SECURITY): 10, (EXTENDED): 7, (SAST): 6, (NEXUS_IQ): 3]
        requested.findAll { it.type == NEXUS_IQ }*.service == ['DOCSENSE extraction-api', 'ACCESSHUB workflow',
                                                               'PAYHUB gateway']
        revoked == [['SAFEKEEP recon-batch', 'Reconciliation moved to the mainframe scheduler'],
                    ['PAYHUB mobile-app', 'Mobile app moved to the new mobile platform pipeline']]

        where:
        jenkinsUrl             || updates
        null                   || 1
        'https://jenkins.test' || 0
    }

    def "CertScanner keeps the Jenkins jobs and agents of its own pipelines"() {
        given:
        demoCatalog('https://jenkins.test')

        when:
        loader.run(null)

        then:
        def certScanner = requested.take(7)
        certScanner*.type == [FULL, SECURITY, EXTENDED, FULL, SECURITY, EXTENDED, SAST]
        certScanner*.action == ['update', 'create', 'create', 'update', 'create', 'create', 'create']
        certScanner[0].settings.agentLabels() == ['linux-agent', 'windows-agent']
        certScanner[0].settings.jenkinsJob() == 'DevSecOps/CertScanner-pipeline'
        certScanner[1].settings.extendedPipelineJob() == 'DevSecOps/CertScanner-extended-pipeline'
        certScanner[4].settings.extendedPipelineJob() == null
        certScanner[5].settings.securityPipelineJob() == 'DevSecOps/CertScanner-security-pipeline'
        requested.drop(6)*.settings*.agentLabels().unique() == [['linux-agent']]
    }

    def "a #type pipeline of #service runs #job and links #extended and #security"() {
        given:
        demoCatalog('https://jenkins.test')

        when:
        loader.run(null)

        then:
        def settings = requested.find { it.service == service && it.type == type }.settings as PipelineSettings
        settings.jenkinsJob() == job
        settings.extendedPipelineJob() == extended
        settings.securityPipelineJob() == security

        where:
        service                   | type     || job                                           | extended                                      | security
        'DOCSENSE extraction-api' | FULL     || 'DevSecOps/DOCSENSE/extraction-api-full'     | null                                          | null
        'DOCSENSE extraction-api' | SECURITY || 'DevSecOps/DOCSENSE/extraction-api-security' | 'DevSecOps/DOCSENSE/extraction-api-extended' | null
        'DOCSENSE extraction-api' | EXTENDED || 'DevSecOps/DOCSENSE/extraction-api-extended' | null                                          | 'DevSecOps/DOCSENSE/extraction-api-security'
        'ADVISORAI assistant-api' | SECURITY || 'DevSecOps/ADVISORAI/assistant-api-security' | null                                          | null
        'NAVCALC nav-api'         | EXTENDED || 'DevSecOps/NAVCALC/nav-api-extended'         | null                                          | null
        'LPPORTAL lp-mobile'      | SAST     || 'DevSecOps/LPPORTAL/lp-mobile-sast'          | null                                          | null
        'DOCSENSE extraction-api' | NEXUS_IQ || 'DevSecOps/DOCSENSE/extraction-api-nexusiq'  | null                                          | null
        'ACCESSHUB workflow'      | NEXUS_IQ || 'DevSecOps/ACCESSHUB/workflow-nexusiq'       | null                                          | null
        'PAYHUB gateway'          | NEXUS_IQ || 'DevSecOps/PAYHUB/gateway-nexusiq'           | null                                          | null
    }

    def "only the demo products a database is missing are added"() {
        given:
        demoCatalog('https://jenkins.test')

        when:
        loader.run(null)

        then:
        1 * products.list(null) >> [summary(1L, 'CERTSCANNER'), summary(2L, 'PAYHUB')]
        0 * settings.update(*_)
        created*.details()*.code() == CODES - ['CERTSCANNER', 'PAYHUB']
        revoked*.first() == ['SAFEKEEP recon-batch']
    }

    def "a demo department that is gone is created again"() {
        given:
        demoCatalog('https://jenkins.test')
        known.removeAll { it.name() != 'Corporate Technology' }

        when:
        loader.run(null)

        then:
        4 * departments.create(_) >> { String name ->
            known << department(known.size() + 10, name)
            known.last()
        }
        created*.details()*.departmentId() == [3L, 11L, 11L, 12L, 12L, 3L, 13L, 13L, 14L, 14L]
        known*.name() == ['Corporate Technology', 'AI Lab', 'Capital Partners', 'Custody', 'Fund Services']
    }

    private void demoCatalog(String jenkinsUrl) {
        products.list(null) >> []
        settings.current() >> storedSettings(jenkinsUrl)
        departments.list() >> { known }
        products.create(_) >> { ProductCommand command -> store(command) }
        pipelines.listForProduct(_) >> { long productId ->
            Product product = stored.find { it.id() == productId }
            product.services().collect { new ServicePipelinesView(it, [view(product, it.id() + 100, it.id(), FULL)]) }
        }
        pipelines.update(_, _, _, _) >> { long id, Long version, PipelineType type, PipelineSettings configured ->
            request('update', id - 100, type, configured, id)
        }
        pipelines.create(_, _, _) >> { long serviceId, PipelineType type, PipelineSettings configured ->
            request('create', serviceId, type, configured, requested.size() + 1000)
        }
        pipelines.revokeKey(_, _) >> { long id, String reason ->
            revoked << [requested.find { it.id == id }.service as String, reason]
            null
        }
    }

    private PipelineView request(String action, long serviceId, PipelineType type, PipelineSettings configured,
                                 long id) {
        Product product = stored.find { it.service(serviceId).present }
        requested << [action: action, type: type, settings: configured, id: id,
                      service: product.code() + ' ' + product.service(serviceId).get().name()]
        view(product, id, serviceId, type)
    }

    private Product store(ProductCommand command) {
        created << command
        long first = stored.sum(0) { it.services().size() } as long
        def services = command.services().withIndex().collect { draft, int order ->
            new Service(first + order + 1, draft.name(), draft.description(), order, draft.settings())
        }
        stored << Product.restore(created.size() as Long, command.details(), command.appScan(), services, 0, null, null)
        stored.last()
    }

    private static ProductSummaryView summary(long id, String code) {
        new ProductSummaryView(id, code, code, null, null, 3L, 'Corporate Technology', 1, 1, 1, null)
    }

    private static DepartmentView department(long id, String name) {
        new DepartmentView(id, name, 0, 0, 0, 0, 0, 0)
    }

    private static PipelineView view(Product product, long id, long serviceId, PipelineType type) {
        PipelineView.of(product, Pipeline.restore(id, new ServiceRef(product.id(), serviceId), type,
                new PipelineSettings(['linux-agent'], null, null, null, null), [], 0, null, null), null)
    }

    private static List<String> problems(settings) {
        def problems = new ValidationProblems()
        settings.validate(problems)
        problems.list()*.field
    }
}
