package com.bbh.itss.dso.portal.smoke

import com.bbh.itss.dso.portal.DsoPortalApplication
import com.bbh.itss.dso.portal.support.PortalClient
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.yaml.snakeyaml.Yaml
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate

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
        !started || !products.json.isEmpty()
    }

    def "the departments answer with the DevSecOps pipelines of their products"() {
        when:
        def departments = api.get('/api/departments')
        def tallies = departments.json.collectEntries {
            [it.name, [it.productCount, it.serviceCount, it.pipelineCount, it.activePipelineCount]]
        }

        then:
        departments.status == 200
        departments.json instanceof List
        !started || tallies == ['AI Lab'              : [2, 4, 8, 8], 'Capital Partners': [2, 5, 9, 9],
                                'Corporate Technology': [2, 4, 10, 10], 'Custody': [2, 4, 8, 7],
                                'Fund Services'       : [2, 6, 11, 10]]
    }

    def "every product's services, pipelines, configuration and change evidence can be read"() {
        given:
        def products = api.get('/api/products').json.take(5)

        expect:
        products.every { product ->
            def details = api.get("/api/products/$product.id")
            def pipelines = api.get("/api/products/$product.id/pipelines")
            def config = api.get("/api/products/$product.id/config")
            def evidence = api.get("/api/evidence/products/$product.id")
            assert details.status == 200: details
            assert pipelines.status == 200: pipelines
            assert config.status == 200: config
            assert evidence.status == 200: evidence
            assert (new Yaml().load(config.body) as Map).projects.size() == details.json.services.size()
            assert evidence.json.services*.name == details.json.services*.name
            true
        }
    }

    def "the global pipeline settings and the configuration they make can be read"() {
        when:
        def settings = api.get('/api/settings')
        def config = api.get('/api/settings/config')

        then:
        settings.status == 200
        settings.json.version >= 0
        settings.json.platform.asocUrl != null
        config.status == 200
        (new Yaml().load(config.body) as Map).keySet() == ['platform', 'defaults'] as Set
    }

    def "every product's ServiceNow change template and Jira epics can be read for a production change"() {
        given:
        def products = api.get('/api/products').json.take(5)
        def range = "from=${LocalDate.now().minusDays(90)}&to=${LocalDate.now()}"

        expect:
        api.get('/api/changes').status == 200
        api.get('/api/changes/integrations').json.keySet() == ['jiraConnected', 'serviceNowConnected'] as Set
        products.every { product ->
            def profile = api.get("/api/products/$product.id/change-profile")
            assert profile.status == 200: profile
            assert !started || profile.json.version != null
            if (profile.json.version != null) {
                def epics = api.get("/api/products/$product.id/jira/epics?$range")
                assert epics.status == 200: epics
            }
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
        def activity = api.get('/api/monitoring/activity?range=30d')

        then:
        status.status == 200
        status.json.containsKey('influxConfigured')
        overview.status == 200
        overview.json.products instanceof List
        activity.status == 200
        activity.json.dora.daily.size() == 30
        !started || overview.json.products.every { it.lastRunAt } && activity.json.dora.runs > 0
    }

    @Requires({ PortalSmokeSpec.uiExpected() })
    def "the web UI is served, also for links into the app"() {
        expect:
        ['/', '/products', '/monitoring', '/evidence', '/settings', '/beadle/changes/new'].every { path ->
            def page = api.get(path)
            assert page.status == 200: "$path: $page.status"
            assert page.header('Content-Type').startsWith('text/html')
            assert page.body.contains('<dso-root')
            true
        }
    }

    static boolean uiExpected() {
        System.getProperty('smoke.ui') != 'false' &&
                (System.getProperty('smoke.baseUrl') || PortalSmokeSpec.getResource('/static/index.html'))
    }
}
