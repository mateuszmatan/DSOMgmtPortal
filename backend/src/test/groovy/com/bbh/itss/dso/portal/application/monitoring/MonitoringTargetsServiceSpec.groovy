package com.bbh.itss.dso.portal.application.monitoring

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class MonitoringTargetsServiceSpec extends Specification {

    ProductRepositoryPort products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineRepositoryPort pipelines = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    @Subject
    def targets = new MonitoringTargetsService(products, pipelines, settings)

    def certScanner = product(id: 1L, services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])
    def payments = product(id: 2L, services: [[name: 'gateway', id: 20L]])
    def guiFull = pipeline(id: 100L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full')
    def apiFull = pipeline(id: 102L, serviceId: 11L)
    def gatewayFull = pipeline(id: 200L, productId: 2L, serviceId: 20L)
    def orphan = pipeline(id: 300L, productId: 9L, serviceId: 90L)

    def "every product and its pipelines are read with the Jenkins the links need"() {
        given:
        products.findAll() >> [certScanner, payments]
        pipelines.findAll() >> [guiFull, apiFull, gatewayFull]

        when:
        def read = targets.everything()

        then:
        read.products() == [certScanner, payments]
        read.pipelines()*.pipeline()*.id() == [100L, 102L, 200L]
        read.pipelines()*.service()*.name() == ['gui', 'backend-api', 'gateway']
        read.platform().jenkinsUrl() == 'https://jenkins.test'
        read.pipelines()[0].jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
    }

    def "a pipeline whose product is gone is left out instead of failing the whole page"() {
        given:
        products.findAll() >> [certScanner]
        pipelines.findAll() >> [guiFull, orphan]

        when:
        def read = targets.everything()

        then:
        read.pipelines()*.pipeline()*.id() == [100L]
    }

    def "one product is read with its own pipelines only"() {
        given:
        products.load(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [guiFull, apiFull]

        when:
        def read = targets.ofProduct(1L)

        then:
        read.product().is(certScanner)
        read.pipelines()*.pipeline()*.id() == [100L, 102L]
        0 * pipelines.findAll()
    }

    def "one pipeline is read with the product it belongs to"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)
        products.load(1L) >> Optional.of(certScanner)

        when:
        def read = targets.ofPipeline(100L)

        then:
        read.pipeline().pipeline().is(guiFull)
        read.pipeline().service().name() == 'gui'
        read.product().is(certScanner)
    }

    def "an unknown #what is reported as not found"() {
        given:
        pipelines.load(100L) >> Optional.of(guiFull)

        when:
        read(targets)

        then:
        def e = thrown(NotFoundException)
        e.message == message

        where:
        what                   | message                        | read
        'product'              | 'Product 5 does not exist'     | { MonitoringTargetsService it -> it.ofProduct(5L) }
        'pipeline'             | 'Pipeline 101 does not exist'  | { MonitoringTargetsService it -> it.ofPipeline(101L) }
        "pipeline's product"   | 'Product 1 does not exist'     | { MonitoringTargetsService it -> it.ofPipeline(100L) }
    }

    def "targets of many products are no targets of one product or one pipeline"() {
        given:
        products.findAll() >> [certScanner, payments]
        pipelines.findAll() >> [guiFull, gatewayFull]
        def read = targets.everything()

        when:
        read.product()

        then:
        thrown(IllegalStateException)

        when:
        read.pipeline()

        then:
        thrown(IllegalStateException)
    }

    def "monitoring targets always name the platform their links are built on"() {
        when:
        new MonitoringTargets([], [], null)

        then:
        def e = thrown(NullPointerException)
        e.message == 'monitoring targets carry the platform settings their links are built on'
    }
}
