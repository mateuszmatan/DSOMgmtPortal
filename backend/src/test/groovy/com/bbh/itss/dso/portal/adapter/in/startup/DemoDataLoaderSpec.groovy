package com.bbh.itss.dso.portal.adapter.in.startup

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
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DemoDataLoaderSpec extends Specification {

    ProductsUseCase products = Mock()
    PipelinesUseCase pipelines = Mock()
    ManageGlobalSettingsUseCase settings = Mock()
    List<ProductCommand> created = []
    List<List> requested = []
    List<Product> stored = []

    def loader = new DemoDataLoader(products, pipelines, settings)

    def "a database with products is left alone"() {
        when:
        loader.run(null)

        then:
        1 * products.list(null) >> [new ProductSummaryView(1L, 'CERT', 'CertScanner', null, null, 1, 1, 1, null)]
        0 * settings.update(*_)
        0 * products.create(_)
        0 * pipelines._
    }

    def "an empty database gets two products with pipelines, one of them invalidated, and a Jenkins to link"() {
        given:
        demoCatalog()

        when:
        loader.run(null)

        then:
        1 * settings.update(null, { it.platform().jenkinsUrl() == 'https://jenkins.bbh.com' })
        1 * pipelines.revokeKey(9L, 'Mobile app moved to the new mobile platform pipeline')
        created*.details()*.code() == ['CERTSCANNER', 'PAYHUB']
        created*.services()*.size() == [2, 4]
        created.every { it.services().every { service -> problems(service.settings()) == [] } }
        requested*.get(1) == [FULL, SAST, FULL, FULL, SECURITY, EXTENDED, FULL, FULL, SAST]
        requested*.get(0) == ['update', 'create', 'update', 'update', 'create', 'create', 'update', 'update', 'create']
        requested.find { it[1] == SECURITY }[2].extendedPipelineJob() == 'DevSecOps/PAYHUB/gateway-extended'
        requested.find { it[1] == EXTENDED }[2].securityPipelineJob() == 'DevSecOps/PAYHUB/gateway-security'
        requested[0][2].jenkinsJob() == 'DevSecOps/CERTSCANNER/gui-full'
        requested.collect { it[2].agentLabels() }.unique() == [['linux-agent']]
    }

    def "a Jenkins already set in the global settings is kept"() {
        given:
        demoCatalog('https://jenkins.test')

        when:
        loader.run(null)

        then:
        0 * settings.update(*_)
    }

    private void demoCatalog(String jenkinsUrl = null) {
        products.list(null) >> []
        settings.current() >> storedSettings(jenkinsUrl)
        products.create(_) >> { ProductCommand command -> store(command) }
        pipelines.listForProduct(_) >> { long productId ->
            Product product = stored.find { it.id() == productId }
            product.services().collect { new ServicePipelinesView(it, [view(product, it.id() + 100, it.id(), FULL)]) }
        }
        pipelines.update(_, _, _) >> { long id, PipelineType type, PipelineSettings configured ->
            requested << ['update', type, configured]
            view(owner(id - 100), id, id - 100, type)
        }
        pipelines.create(_, _, _) >> { long serviceId, PipelineType type, PipelineSettings configured ->
            requested << ['create', type, configured]
            view(owner(serviceId), requested.size(), serviceId, type)
        }
    }

    private Product owner(long serviceId) {
        stored.find { it.service(serviceId).present }
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

    private static PipelineView view(Product product, long id, long serviceId, PipelineType type) {
        PipelineView.of(product, Pipeline.restore(id, new ServiceRef(product.id(), serviceId), type,
                PipelineSettings.forNewService(), [], 0, null, null), null)
    }

    private static List<String> problems(settings) {
        def problems = new ValidationProblems()
        settings.validate(problems)
        problems.list()*.field
    }
}
