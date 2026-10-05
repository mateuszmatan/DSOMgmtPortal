package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.catalog.port.in.ManageProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ServiceCommand
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineCommand
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.UpdateGlobalSettingsCommand
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DemoDataLoaderSpec extends Specification {

    QueryProductsUseCase products = Mock()
    ManageProductsUseCase catalog = Mock()
    ManagePipelinesUseCase pipelines = Mock()
    ManagePipelineKeysUseCase keys = Mock()
    ManageGlobalSettingsUseCase settings = Mock()

    List<ProductCommand> created = []
    List<PipelineCommand> requested = []
    Map<Long, Product> owners = [:]
    long serviceIds = 0
    long pipelineIds = 0

    @Subject
    def loader = new DemoDataLoader(products, catalog, pipelines, keys, settings)

    def "a database with products is left alone"() {
        when:
        loader.run(null)

        then:
        1 * products.list(null) >> [new ProductSummaryView(1L, 'CERT', 'CertScanner', null, null, 1, 1, 1, null)]
        0 * settings.update(*_)
        0 * catalog.create(_)
        0 * pipelines.create(*_)
    }

    def "an empty database gets two products with pipelines, one of them invalidated, and a Jenkins to link"() {
        given:
        products.list(null) >> []
        settings.current() >> storedSettings()
        catalog.create(_) >> { ProductCommand command -> stored(command) }
        pipelines.create(_, _) >> { Long serviceId, PipelineCommand command -> view(serviceId, command) }

        when:
        loader.run(null)

        then:
        1 * settings.update({ UpdateGlobalSettingsCommand command ->
            command.expectedVersion() == null && command.values().platform().jenkinsUrl() == 'https://jenkins.bbh.com'
        })
        1 * keys.revokeKey(9L, 'Mobile app moved to the new mobile platform pipeline')
        created*.details()*.code() == ['CERTSCANNER', 'PAYHUB']
        created*.version() == [null, null]
        created*.services()*.size() == [2, 4]
        created.every { product -> product.services().every { validProblems(it) == [] } }
        requested*.type() == [PipelineType.FULL, PipelineType.SAST, PipelineType.FULL, PipelineType.FULL,
                              PipelineType.SECURITY, PipelineType.EXTENDED, PipelineType.FULL, PipelineType.FULL,
                              PipelineType.SAST]
        requested.find { it.type() == PipelineType.SECURITY }.settings().extendedPipelineJob() ==
                'DevSecOps/PAYHUB/gateway-extended'
        requested.find { it.type() == PipelineType.EXTENDED }.settings().securityPipelineJob() ==
                'DevSecOps/PAYHUB/gateway-security'
        requested[0].settings().jenkinsJob() == 'DevSecOps/CERTSCANNER/gui-full'
        requested*.settings()*.agentLabels().unique() == [['linux-agent']]
    }

    def "a Jenkins already set in the global settings is kept"() {
        given:
        products.list(null) >> []
        settings.current() >> storedSettings('https://jenkins.test')
        catalog.create(_) >> { ProductCommand command -> stored(command) }
        pipelines.create(_, _) >> { Long serviceId, PipelineCommand command -> view(serviceId, command) }

        when:
        loader.run(null)

        then:
        0 * settings.update(*_)
    }

    private Product stored(ProductCommand command) {
        created << command
        List<Service> services = command.services().withIndex().collect { ServiceCommand service, int order ->
            new Service(++serviceIds, service.name(), service.description(), order, service.settings())
        }
        Product product = Product.restore(created.size() as Long, command.details(), command.appScan(), services, 0,
                null, null)
        services.each { owners[it.id()] = product }
        product
    }

    private PipelineView view(Long serviceId, PipelineCommand command) {
        requested << command
        Product product = owners[serviceId]
        PipelineView.of(product, Pipeline.restore(++pipelineIds, new ServiceRef(product.id(), serviceId), command.type(),
                command.settings(), [], 0, null, null), null)
    }

    private static List<String> validProblems(ServiceCommand service) {
        def problems = new ValidationProblems()
        service.settings().validate(problems)
        problems.list()*.field
    }
}
