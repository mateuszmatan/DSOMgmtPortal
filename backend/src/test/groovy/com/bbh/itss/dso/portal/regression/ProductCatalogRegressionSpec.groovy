package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service

/**
 * The product management API end to end: HTTP, validation, JPA and the database schema created by Flyway.
 */
class ProductCatalogRegressionSpec extends PortalSpecification {

    def "a product with its services is created, read back and found by search"() {
        given:
        def code = uniqueCode()

        when:
        def created = api.post('/api/products', product(code: code, name: "Product $code", ownerTeam: 'Payments Engineering',
                services: [service(name: 'gateway'), service(name: 'ledger', build: [tool: 'MAVEN', autoSetup: true])]))

        then:
        created.status == 201
        created.header('Location') == "$api.baseUrl/api/products/${created.json.id}"
        with(created.json) {
            it.code == code
            version == 0
            createdAt != null
            services*.name == ['gateway', 'ledger']
            services*.metrics*.influxProject == ["$code-gateway", "$code-ledger"]
            services[1].build == [tool: 'MAVEN', sourceDir: '.', javaPath: null, autoSetup: true]
            services[0].scm.goldenFixEnabled == true
        }

        and:
        api.get("/api/products/${created.json.id}").json == created.json
        api.get("/api/products?search=${code.toLowerCase()}").json*.code == [code]
        with(api.get('/api/products').json.find { it.code == code }) {
            serviceCount == 2
            pipelineCount == 0
            ownerTeam == 'Payments Engineering'
        }
    }

    def "an update changes kept services, adds new ones and removes the missing ones"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'api'), service(name: 'batch')]))
        def ids = created.services.collectEntries { [it.name, it.id] }
        def guiMetrics = created.services.find { it.name == 'gui' }.metrics

        when: 'gui is renamed and sent back with its metrics tags, as the portal UI does, so its history stays'
        def updated = api.put("/api/products/$created.id", product(code: code, name: "Renamed $code", version: created.version,
                services: [service(id: ids.api, name: 'api', description: 'REST API'),
                           service(name: 'worker'),
                           service(id: ids.gui, name: 'web', metrics: guiMetrics)]))

        then:
        updated.status == 200
        with(updated.json) {
            name == "Renamed $code"
            version == created.version + 1
            services*.name == ['api', 'worker', 'web']
            services*.id[0] == ids.api
            services*.id[2] == ids.gui
            services[0].description == 'REST API'
            services[2].metrics.influxProject == "$code-gui"
        }
        !updated.json.services*.id.contains(ids.batch)
    }

    def "a removed service frees its name and metrics tags for a new one"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code", services: [service(name: 'gui')]))

        when:
        def updated = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                services: [service(name: 'gui', description: 'new service with the old name')]))

        then:
        updated.status == 200
        updated.json.services[0].id != created.services[0].id
        updated.json.services[0].metrics.influxProject == "$code-gui"
    }

    def "an update based on an outdated version is refused"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))
        api.put("/api/products/$created.id", product(code: code, name: "First change $code", version: 0))

        when:
        def stale = api.put("/api/products/$created.id", product(code: code, name: "Second change $code", version: 0))

        then:
        stale.status == 409
        stale.json.detail.contains('changed by someone else')
        api.get("/api/products/$created.id").json.name == "First change $code"
    }

    def "product codes and names are unique regardless of case"() {
        given:
        def code = uniqueCode()
        createProduct(product(code: code, name: "Product $code"))

        expect:
        api.post('/api/products', product(code: code, name: "Other $code")).status == 409
        api.post('/api/products', product(code: uniqueCode(), name: "product ${code.toLowerCase()}")).status == 409
    }

    def "every rule a service breaks is reported against its field"() {
        given:
        def code = uniqueCode()
        def other = createProduct(product(code: uniqueCode(), name: "Other ${uniqueCode()}",
                services: [service(name: 'gui', sonar: [projectKey: "sonar-$code"])]))

        when:
        def response = api.post('/api/products', product(code: code, name: "Product $code", services: [
                service(name: 'gui', build: [tool: 'GRADLE']),
                service(name: 'api', deployment: [target: 'OPENSHIFT'], sonar: [projectKey: "sonar-$code"]),
                service(name: 'batch', appScan: [applicationId: com.bbh.itss.dso.portal.support.Fixtures.APP_ID, dastEnabled: true],
                        additionalConfig: [yaml: 'influx:\n  token: abc\nunknownKey: 1']),
                service(name: 'gui', metrics: [influxProject: other.services[0].metrics.influxProject])]))

        then:
        response.status == 400
        response.json.errors*.field == ['services[0].build.javaPath',
                                        'services[1].deployment.appName', 'services[1].deployment.artifactName',
                                        'services[1].sonar.projectKey',
                                        'services[2].appScan.dastTargetUrl', 'services[2].additionalConfig.yaml',
                                        'services[2].additionalConfig.yaml',
                                        'services[3].name', 'services[3].metrics.influxProject']
        api.get("/api/products?search=$code").json == []
    }

    def "malformed input is refused before it reaches the business rules"() {
        expect:
        api.post('/api/products', body).status == 400

        where:
        body << [product(code: 'lower-case'),
                 product(services: [service(build: [tool: 'ANT'])]),
                 product(services: [service(appScan: [applicationId: 'not-a-uuid'])]),
                 product(appScan: null),
                 [code: 'X']]
    }

    def "a deleted product is gone together with its pipelines and keys"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))
        def pipeline = createPipeline(created.services[0].id)

        when:
        def deleted = api.delete("/api/products/$created.id")

        then:
        deleted.status == 204
        api.get("/api/products/$created.id").status == 404
        api.get("/api/pipelines/$pipeline.id").status == 404
        api.get("/api/dso/config/$pipeline.activeKey.value").status == 404
    }
}
