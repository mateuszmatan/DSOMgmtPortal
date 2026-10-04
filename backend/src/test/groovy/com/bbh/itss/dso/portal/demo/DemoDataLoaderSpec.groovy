package com.bbh.itss.dso.portal.demo

import com.bbh.itss.dso.portal.catalog.ProductCatalogService
import com.bbh.itss.dso.portal.catalog.ProductRepository
import com.bbh.itss.dso.portal.catalog.ProductRequest
import com.bbh.itss.dso.portal.catalog.ProductResponse
import com.bbh.itss.dso.portal.catalog.ServiceResponse
import com.bbh.itss.dso.portal.common.ValidationProblems
import com.bbh.itss.dso.portal.pipeline.PipelineRequest
import com.bbh.itss.dso.portal.pipeline.PipelineResponse
import com.bbh.itss.dso.portal.pipeline.PipelineService
import com.bbh.itss.dso.portal.pipeline.PipelineType
import spock.lang.Specification
import spock.lang.Subject

class DemoDataLoaderSpec extends Specification {

    ProductRepository products = Mock()
    ProductCatalogService catalog = Mock()
    PipelineService pipelines = Mock()

    @Subject
    def loader = new DemoDataLoader(products, catalog, pipelines)

    def "a database with products is left alone"() {
        when:
        loader.run(null)

        then:
        1 * products.count() >> 1
        0 * catalog.create(_)
        0 * pipelines.create(*_)
    }

    def "an empty database gets two products with pipelines, one of them invalidated"() {
        given:
        List<ProductRequest> created = []
        List<PipelineRequest> requested = []
        long serviceIds = 0
        long pipelineIds = 0
        products.count() >> 0
        catalog.create(_) >> { ProductRequest request ->
            created << request
            new ProductResponse(created.size() as Long, request.code(), request.name(), null, null, null, request.appScan(), 0,
                    null, null, request.services().collect {
                new ServiceResponse(++serviceIds, it.name(), null, it.build(), it.deployment(), it.appScan(), it.sonar(),
                        it.nexusIq(), it.scm(), it.metrics(), it.additionalConfig())
            })
        }
        pipelines.create(_, _) >> { Long serviceId, PipelineRequest request ->
            requested << request
            new PipelineResponse(++pipelineIds, null, null, null, serviceId, null, request.type(), null, request.agentLabels(),
                    request.extendedPipelineJob(), null, true, null, null, null, null, null, null)
        }

        when:
        loader.run(null)

        then:
        1 * pipelines.revokeKey(8L, 'Mobile app moved to the new mobile platform pipeline')
        created*.code() == ['CERTSCANNER', 'PAYHUB']
        created*.services()*.size() == [2, 4]
        created.every { product -> product.services().every { validProblems(it) == [] } }
        requested*.type() == [PipelineType.FULL, PipelineType.SAST, PipelineType.FULL, PipelineType.FULL,
                              PipelineType.SECURITY, PipelineType.FULL, PipelineType.FULL, PipelineType.SAST]
        requested.find { it.type() == PipelineType.SECURITY }.extendedPipelineJob() == 'PAYHUB/gateway-extended'
        requested.findAll { it.type() != PipelineType.SECURITY }.every { it.extendedPipelineJob() == null }
    }

    private static List<String> validProblems(service) {
        def problems = new ValidationProblems()
        service.settings().validate(problems)
        problems.list()*.field
    }
}
