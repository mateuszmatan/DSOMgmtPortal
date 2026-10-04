package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification
import org.yaml.snakeyaml.Yaml

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service

/**
 * Pipelines and their keys end to end, including the endpoint the DevSecOps library will call.
 */
class PipelineKeyRegressionSpec extends PortalSpecification {

    String code
    Map certScanner
    long gui

    def setup() {
        code = uniqueCode()
        certScanner = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'backend-api')]))
        gui = certScanner.services[0].id as long
    }

    def "a new pipeline's key fetches its configuration until it is invalidated"() {
        given:
        def created = createPipeline(gui, pipeline(agentLabels: ['linux-agent', 'docker']))
        String key = created.activeKey.value

        when:
        def config = api.get("/api/dso/config/$key")

        then:
        created.enabled
        key ==~ /[0-9a-f-]{36}/
        config.status == 200
        config.header('Content-Type').startsWith('application/yaml')
        def yaml = new Yaml().load(config.body) as Map
        yaml.pipeline == [type: 'full', entryPoint: 'devSecOpsPipeline', product: code, projectNames: 'gui',
                          agentNames: ['linux-agent', 'docker']]
        yaml.projects.keySet() == ['gui'] as Set
        yaml.projects.gui.influx.project == "$code-gui"

        when:
        def revoked = api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'Leaked in a build log'])
        def refused = api.get("/api/dso/config/$key")

        then:
        revoked.status == 200
        !revoked.json.enabled
        revoked.json.activeKey == null
        refused.status == 403
        refused.json.title == 'Pipeline key invalidated'
        refused.json.detail.endsWith(': Leaked in a build log')
    }

    def "a new key replaces the old one, which stays refused"() {
        given:
        def created = createPipeline(gui)
        String oldKey = created.activeKey.value

        when:
        def reissued = api.post("/api/pipelines/$created.id/keys")
        String newKey = reissued.json.activeKey.value

        then:
        reissued.status == 200
        newKey != oldKey
        reissued.json.keys*.status == ['ACTIVE', 'REVOKED']
        reissued.json.keys[1].revokeReason == 'Replaced by a new key'
        api.get("/api/dso/config/$oldKey").status == 403
        api.get("/api/dso/config/$newKey").status == 200
    }

    def "fetching the configuration records when the key was last used"() {
        given:
        def created = createPipeline(gui)

        when:
        api.get("/api/dso/config/${created.activeKey.value.toUpperCase()}?format=json")

        then:
        created.activeKey.lastUsedAt == null
        api.get("/api/pipelines/$created.id").json.activeKey.lastUsedAt != null
    }

    def "an unknown key is 404 and revoking twice is 409"() {
        given:
        def created = createPipeline(gui)
        api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'retired'])

        expect:
        api.get('/api/dso/config/00000000-0000-4000-8000-000000000000').status == 404
        api.post("/api/pipelines/$created.id/keys/revoke", [reason: 'again']).status == 409
    }

    def "a service has one pipeline of each type and a pipeline keeps its type"() {
        given:
        def full = createPipeline(gui)

        expect:
        api.post("/api/services/$gui/pipelines", pipeline()).status == 409
        api.put("/api/pipelines/$full.id", pipeline(type: 'SAST')).status == 409
        createPipeline(gui, pipeline(type: 'SAST')).type == 'SAST'
    }

    def "the security pipeline passes on its extended pipeline job"() {
        when:
        def security = createPipeline(gui, pipeline(type: 'SECURITY', extendedPipelineJob: "$code/gui-extended"))
        def full = createPipeline(gui, pipeline(type: 'FULL', extendedPipelineJob: 'ignored'))
        def config = new Yaml().load(api.get("/api/dso/config/$security.activeKey.value").body) as Map

        then:
        security.influxProjectTag == "$code-guisecurity"
        full.extendedPipelineJob == null
        config.projects.gui.jenkins.pipeline.extendedPipeline == "$code/gui-extended"
    }

    def "the product's pipelines are listed per service"() {
        given:
        createPipeline(gui)
        createPipeline(gui, pipeline(type: 'SAST'))

        when:
        def list = api.get("/api/products/$certScanner.id/pipelines").json

        then:
        list*.serviceName == ['gui', 'backend-api']
        list[0].pipelines*.type == ['FULL', 'SAST']
        list[1].pipelines == []
        api.get('/api/products').json.find { it.code == code }.pipelineCount == 2
    }

    def "removing a service from the product removes its pipelines"() {
        given:
        def created = createPipeline(certScanner.services[1].id as long)

        when:
        api.put("/api/products/$certScanner.id", product(code: code, name: "Product $code",
                services: [service(id: gui, name: 'gui')]))

        then:
        api.get("/api/pipelines/$created.id").status == 404
        api.get("/api/dso/config/$created.activeKey.value").status == 404
    }

    def "concurrent key changes leave exactly one active key"() {
        given:
        def created = createPipeline(gui)
        def pool = Executors.newFixedThreadPool(10)

        when:
        def statuses = pool.invokeAll((1..20).collect { { -> api.post("/api/pipelines/$created.id/keys").status } as Callable<Integer> })*.get()
        def keys = api.get("/api/pipelines/$created.id").json.keys

        then:
        statuses.every { it == 200 }
        keys.size() == 21
        keys.count { it.status == 'ACTIVE' } == 1

        cleanup:
        pool.shutdownNow()
    }
}
