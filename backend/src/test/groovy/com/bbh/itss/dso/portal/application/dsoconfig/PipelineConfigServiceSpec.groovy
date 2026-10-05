package com.bbh.itss.dso.portal.application.dsoconfig

import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.ReadPublishedConfigUseCase
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
    ReadPublishedConfigUseCase published = Mock()
    QueryPipelinesUseCase pipelines = Mock()
    QueryProductsUseCase products = Mock()
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    @Subject
    def service = new PipelineConfigService(keys, published, pipelines, products, settings)

    Product certScanner = product(id: 1L, code: 'CERT', services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])

    def "a pipeline key reads the configuration its pipeline published"() {
        given:
        Map<String, Object> current = [pipeline: [type: 'full', product: 'CERT']]

        when:
        def config = service.readByKey(KEY)

        then:
        1 * keys.authorizeKey(KEY) >> 100L
        1 * published.currentConfig(100L) >> Optional.of(current)
        0 * pipelines._
        0 * products._
        config.is(current)
    }

    def "a pipeline key renders the configuration of its pipeline when none is published since the start"() {
        when:
        def config = service.readByKey(KEY)

        then:
        1 * keys.authorizeKey(KEY) >> 100L
        1 * published.currentConfig(100L) >> Optional.empty()
        1 * pipelines.get(100L) >> view(PipelineType.FULL)
        config.keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
        config.pipeline.product == 'CERT'
        config.pipeline.projectNames == 'gui'
        config.platform.jenkinsUrl == 'https://jenkins.test'
        config.projects.keySet() as List == ['gui']
    }

    def "an invalidated or unknown key reads no configuration"() {
        given:
        keys.authorizeKey(KEY) >> { throw failure }

        when:
        service.readByKey(KEY)

        then:
        thrown(failure.class)
        0 * published._
        0 * pipelines._

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
