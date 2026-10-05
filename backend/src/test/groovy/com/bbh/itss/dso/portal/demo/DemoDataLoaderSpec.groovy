package com.bbh.itss.dso.portal.demo

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.UpdateGlobalSettingsCommand
import com.bbh.itss.dso.portal.catalog.ProductCatalogService
import com.bbh.itss.dso.portal.catalog.ProductRepository
import com.bbh.itss.dso.portal.catalog.ProductRequest
import com.bbh.itss.dso.portal.catalog.ProductResponse
import com.bbh.itss.dso.portal.catalog.ServiceResponse
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import com.bbh.itss.dso.portal.pipeline.PipelineRequest
import com.bbh.itss.dso.portal.pipeline.PipelineResponse
import com.bbh.itss.dso.portal.pipeline.PipelineService
import com.bbh.itss.dso.portal.pipeline.PipelineType
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DemoDataLoaderSpec extends Specification {

    ProductRepository products = Mock()
    ProductCatalogService catalog = Mock()
    PipelineService pipelines = Mock()
    ManageGlobalSettingsUseCase settings = Mock()

    @Subject
    def loader = new DemoDataLoader(products, catalog, pipelines, settings)

    def "a database with products is left alone"() {
        when:
        loader.run(null)

        then:
        1 * products.count() >> 1
        0 * settings.update(*_)
        0 * catalog.create(_)
        0 * pipelines.create(*_)
    }

    def "an empty database gets two products with pipelines, one of them invalidated, and a Jenkins to link"() {
        given:
        List<ProductRequest> created = []
        List<PipelineRequest> requested = []
        long serviceIds = 0
        long pipelineIds = 0
        products.count() >> 0
        settings.current() >> storedSettings()
        catalog.create(_) >> { ProductRequest request ->
            created << request
            new ProductResponse(created.size() as Long, request.code(), request.name(), null, null, null, request.appScan(), 0,
                    null, null, request.services().collect {
                new ServiceResponse(++serviceIds, it.name(), null, it.build(), it.unitTests(), it.tests(), it.testJobs(),
                        it.deployment(), it.delivery(), it.urbanCode(), it.urbanCodeApplications(), it.sshTargets(),
                        it.openShiftTargets(), it.appScan(), it.sonar(), it.nexusIq(), it.scm(), it.goldenFix(),
                        it.metrics(), it.flutter())
            })
        }
        pipelines.create(_, _) >> { Long serviceId, PipelineRequest request ->
            requested << request
            new PipelineResponse(++pipelineIds, null, null, null, serviceId, null, request.type(), null,
                    request.agentLabels(), request.extendedPipelineJob(), request.securityPipelineJob(),
                    request.jenkinsJob(), null, null, true, null, null, null, null, null, null)
        }

        when:
        loader.run(null)

        then:
        1 * settings.update({ UpdateGlobalSettingsCommand command ->
            command.expectedVersion() == null && command.values().platform().jenkinsUrl() == 'https://jenkins.bbh.com'
        })
        1 * pipelines.revokeKey(9L, 'Mobile app moved to the new mobile platform pipeline')
        created*.code() == ['CERTSCANNER', 'PAYHUB']
        created*.services()*.size() == [2, 4]
        created.every { product -> product.services().every { validProblems(it) == [] } }
        requested*.type() == [PipelineType.FULL, PipelineType.SAST, PipelineType.FULL, PipelineType.FULL,
                              PipelineType.SECURITY, PipelineType.EXTENDED, PipelineType.FULL, PipelineType.FULL,
                              PipelineType.SAST]
        requested.find { it.type() == PipelineType.SECURITY }.extendedPipelineJob() == 'DevSecOps/PAYHUB/gateway-extended'
        requested.find { it.type() == PipelineType.EXTENDED }.securityPipelineJob() == 'DevSecOps/PAYHUB/gateway-security'
        requested[0].jenkinsJob() == 'DevSecOps/CERTSCANNER/gui-full'
    }

    def "a Jenkins already set in the global settings is kept"() {
        given:
        products.count() >> 0
        settings.current() >> storedSettings('https://jenkins.test')
        catalog.create(_) >> { ProductRequest request ->
            new ProductResponse(1L, request.code(), request.name(), null, null, null, request.appScan(), 0, null, null,
                    request.services().collect { new ServiceResponse(1L, it.name(), null, it.build(), null, null, null,
                            it.deployment(), null, null, null, null, null, it.appScan(), null, null, null, null, null,
                            null) })
        }
        pipelines.create(_, _) >> new PipelineResponse(1L, null, null, null, 1L, null, PipelineType.SAST, null, [],
                null, null, null, null, null, true, null, null, null, null, null, null)

        when:
        loader.run(null)

        then:
        0 * settings.update(*_)
    }

    private static List<String> validProblems(service) {
        def problems = new ValidationProblems()
        service.settings().validate(problems)
        problems.list()*.field
    }
}
