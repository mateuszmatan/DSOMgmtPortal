package com.bbh.itss.dso.portal.smoke

import com.bbh.itss.dso.portal.DsoPortalApplication
import com.bbh.itss.dso.portal.support.PortalClient
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.yaml.snakeyaml.Yaml
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

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
        !started || tallies == ['AI Lab'              : [2, 4, 9, 9], 'Capital Partners': [2, 5, 9, 9],
                                'Corporate Technology': [2, 4, 11, 11], 'Custody': [2, 4, 8, 7],
                                'Fund Services'       : [2, 6, 12, 11]]
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

    def "every product's ProTech change template and Jira FixVersions and epics can be read for a production change"() {
        given:
        def products = api.get('/api/products').json.take(5)

        expect:
        api.get('/api/changes').status == 200
        api.get('/api/changes/integrations').json.keySet() ==
                ['jiraConnected', 'serviceNowConnected', 'cyberTrackConnected'] as Set
        api.get('/api/change-profiles').status == 200
        !started || api.get('/api/change-profiles').json.size() >= products.size()
        products.every { product ->
            def profile = api.get("/api/products/$product.id/change-profile")
            assert profile.status == 200: profile
            assert !started || profile.json.version != null
            assert !profile.json.tasks.isEmpty()
            assert profile.json.tasks.every { it.assignmentGroup && it.shortDescription && it.description }
            assert !started || profile.json.tasks.any { it.assignmentGroup.toLowerCase().contains('release management') }
            if (profile.json.version != null) {
                def versions = api.get("/api/products/$product.id/jira/versions")
                assert versions.status == 200: versions
                versions.json.take(1).each { version ->
                    def epics = api.get("/api/products/$product.id/jira/epics?fixVersion=${encode(version.name, UTF_8)}")
                    assert epics.status == 200: epics
                }
            }
            true
        }
    }

    def "the demo changes are stored, synced with ProTech, spread over its workflow and hold their change tasks"() {
        when:
        def changes = api.get('/api/changes')
        def department = changes.json.find { it.departmentId != null }?.departmentId
        def tasks = changes.json.collectMany { it.tasks }

        then:
        changes.status == 200
        changes.json.every { it.syncProblem == null }
        !started || changes.json.size() >= 12 && changes.json*.state.toSet().size() >= 6 &&
                changes.json*.update.findAll()*.status.toSet() == ['APPLIED', 'NOT_APPLIED'] as Set
        tasks.every { it.number ==~ /CTASK\d{7}/ && it.details.assignmentGroup && it.approval }
        tasks*.approval.toSet().every { it in ['Not Yet Requested', 'Requested', 'Approved'] }
        !started || changes.json.every { !it.tasks.isEmpty() } &&
                tasks*.approval.toSet().containsAll(['Not Yet Requested', 'Approved'])
        department == null || api.get("/api/changes?departmentId=$department").json.every {
            it.departmentId == department
        }
    }

    def "the wizard reads the signed-in user, its options and lookups that know the people of the demo templates"() {
        given:
        def products = api.get('/api/products').json

        expect:
        api.get('/api/me').json.name
        api.get('/api/changes/options').json.categories.contains('Application')
        api.get('/api/changes/options').json.releaseManagement == 'Release Management'
        ['users', 'departments', 'assignment-groups', 'releases', 'configuration-items', 'incidents', 'problems',
         'clients'].every { api.get("/api/lookups/$it").status == 200 }
        !started || products.every { product ->
            def template = api.get("/api/products/$product.id/change-profile").json.template
            def people = (template.approvers.values() + template.privilegedAccess.users*.user).findAll()
            def item = api.get("/api/lookups/configuration-items?q=${encode(template.configurationItem, UTF_8)}").json
            assert people.every { api.get("/api/lookups/users?q=${encode(it, UTF_8)}").json*.value.contains(it) }
            assert template.directBusinessService == item.find { it.value == template.configurationItem }?.detail
            assert template.risk in ['Low', 'Moderate', 'High']
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
        ['/', '/self-service', '/admin/products', '/monitoring', '/evidence', '/admin/settings',
         '/beadle/changes', '/beadle/changes/new', '/beadle/new-change', '/beadle/admin'].every { path ->
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
