package com.bbh.dso.portal.smoke

import com.bbh.dso.portal.DsoPortalApplication
import com.bbh.dso.portal.support.PortalClient
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.yaml.snakeyaml.Yaml
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

/**
 * Quick read-only checks that a started portal works. Point it at a deployment with
 * {@code -Dsmoke.baseUrl=https://dso-portal.bbh.com}; without it the portal is started here with the local
 * profile and its demo data. {@code -Dsmoke.ui=false} skips the web UI check for a back end without it.
 */
class PortalSmokeSpec extends Specification {

    @Shared
    ConfigurableApplicationContext started

    @Shared
    PortalClient api

    def setupSpec() {
        String baseUrl = System.getProperty('smoke.baseUrl')
        if (!baseUrl) {
            started = new SpringApplicationBuilder(DsoPortalApplication)
                    .profiles('local')
                    .run('--server.port=0', '--dso.demo-data=true',
                            '--spring.datasource.url=jdbc:h2:mem:dso-smoke;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1')
            baseUrl = "http://localhost:${started.environment.getProperty('local.server.port')}"
        }
        api = new PortalClient(baseUrl)
    }

    def cleanupSpec() {
        started?.close()
    }

    def "the application reports itself healthy"() {
        when:
        def health = api.get('/actuator/health')

        then:
        health.status == 200
        health.json.status == 'UP'
    }

    def "the product catalog answers"() {
        when:
        def products = api.get('/api/products')

        then:
        products.status == 200
        products.json instanceof List
        started == null || !products.json.isEmpty()
    }

    def "every product's services, pipelines and config.yaml can be read"() {
        given:
        def products = api.get('/api/products').json.take(5)

        expect:
        products.every { product ->
            def details = api.get("/api/products/$product.id")
            def pipelines = api.get("/api/products/$product.id/pipelines")
            def config = api.get("/api/products/$product.id/config")
            assert details.status == 200: details
            assert pipelines.status == 200: pipelines
            assert config.status == 200: config
            assert (new Yaml().load(config.body) as Map).projects.size() == details.json.services.size()
            true
        }
    }

    def "a pipeline key that was never issued is refused"() {
        expect:
        api.get('/api/dso/config/00000000-0000-4000-8000-000000000000').status == 404
    }

    def "the monitoring pages answer, with or without InfluxDB"() {
        when:
        def status = api.get('/api/monitoring/status')
        def overview = api.get('/api/monitoring/products')

        then:
        status.status == 200
        status.json.containsKey('influxConfigured')
        overview.status == 200
        overview.json.products instanceof List
    }

    @Requires({ PortalSmokeSpec.uiExpected() })
    def "the web UI is served, also for links into the app"() {
        expect:
        ['/', '/products', '/monitoring'].every { path ->
            def page = api.get(path)
            assert page.status == 200: "$path: $page.status"
            assert page.header('Content-Type').startsWith('text/html')
            assert page.body.contains('<dso-root')
            true
        }
    }

    static boolean uiExpected() {
        System.getProperty('smoke.ui') != 'false' &&
                (System.getProperty('smoke.baseUrl') || PortalSmokeSpec.getResource('/static/index.html') != null)
    }
}
