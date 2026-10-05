package com.bbh.itss.dso.portal.application.dsoconfig

import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.pipeline.KeyRevokedException
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.UPDATED
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class PipelineConfigServiceSpec extends Specification {

    static final String KEY = '6f1c2d3e-0000-4abc-9def-123456789abc'

    ManagePipelineKeysUseCase keys = Mock()
    QueryPipelinesUseCase pipelines = Mock()
    QueryProductsUseCase products = Mock()
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    @Subject
    def service = new PipelineConfigService(keys, pipelines, products, settings)

    Product certScanner = product(id: 1L, code: 'CERT', services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])

    def "a pipeline key reads the configuration of its pipeline"() {
        when:
        def config = service.readByKey(KEY)

        then:
        1 * keys.resolveKey(KEY) >> view(PipelineType.FULL)
        config.keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
        config.pipeline.product == 'CERT'
        config.pipeline.projectNames == 'gui'
        config.platform.jenkinsUrl == 'https://jenkins.test'
        config.projects.keySet() as List == ['gui']
    }

    def "an invalidated or unknown key reads no configuration"() {
        given:
        keys.resolveKey(KEY) >> { throw failure }

        when:
        service.readByKey(KEY)

        then:
        thrown(failure.class)

        where:
        failure << [new KeyRevokedException(pipeline().revokeActiveKey('Service retired', UPDATED)),
                    new NotFoundException('Unknown DevSecOps pipeline key')]
    }

    def "the portal previews a pipeline's configuration by its id"() {
        when:
        def config = service.pipelineConfig(100L)

        then:
        1 * pipelines.get(100L) >> view(PipelineType.SECURITY)
        0 * keys._
        config.pipeline.type == 'security'
    }

    def "a product's configuration lists all of its services"() {
        when:
        def config = service.productConfig(1L)

        then:
        1 * products.get(1L) >> certScanner
        config.projects.keySet() as List == ['gui', 'backend-api']
    }

    def "the settings configuration holds the platform and the defaults"() {
        expect:
        service.settingsConfig().keySet() as List == ['platform', 'defaults']
    }

    private PipelineView view(PipelineType type) {
        PipelineView.of(certScanner, pipeline(productId: 1L, serviceId: 10L, type: type), 'https://jenkins.test')
    }
}
