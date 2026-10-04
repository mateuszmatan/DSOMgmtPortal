package com.bbh.itss.dso.portal.performance

import com.bbh.itss.dso.portal.support.FakeInfluxDb
import com.bbh.itss.dso.portal.support.LatencyStats
import com.bbh.itss.dso.portal.support.PortalSpecification
import spock.lang.Shared
import spock.lang.Stepwise

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.fullMavenService
import static com.bbh.itss.dso.portal.support.ApiJson.product

/**
 * Response times under a realistic load: 25 products of 16 services with every setting filled in, a pipeline for
 * every service and bursts of configuration requests as when many Jenkins jobs start at once. The limits are generous so the suite
 * catches regressions such as a query per row rather than measuring the machine; scale them with
 * {@code -Dperformance.factor=2} on a slow agent. The figures are written to target/performance-report.md.
 */
@Stepwise
class PortalPerformanceSpec extends PortalSpecification {

    static final int PRODUCTS = 25
    static final int SERVICES = 16
    static final double FACTOR = (System.getProperty('performance.factor') ?: '1') as double
    static final File REPORT = new File('target/performance-report.md')

    @Shared
    List<Map> products = [].asSynchronized()

    @Shared
    List<Map> pipelines = [].asSynchronized()

    @Shared
    List<LatencyStats> results = []

    /** When the latest run of every pipeline finished, as recorded in InfluxDB by the monitoring step. */
    @Shared
    Instant lastRunsFinishedAt

    def setupSpec() {
        FakeInfluxDb.shared().reset()
    }

    def cleanupSpec() {
        REPORT.parentFile.mkdirs()
        REPORT.text = "# Portal performance\n\n$PRODUCTS products with $SERVICES services each, " +
                "${PRODUCTS * SERVICES} pipelines, limits scaled by $FACTOR.\n\n" +
                LatencyStats.header() + '\n' + results*.toRow().join('\n') + '\n'
        println REPORT.text
    }

    def "products with 16 services are created quickly"() {
        when:
        def stats = LatencyStats.measure("Create a product with $SERVICES services", calls: PRODUCTS, warmUp: false) { int i ->
            def code = uniqueCode('PERF')
            def response = api.post('/api/products', product(code: code, name: "Performance $code",
                    services: (1..SERVICES).collect { fullService(code, "service-$it") }))
            if (response.status == 201) {
                products << (response.json as Map)
            }
            response.status == 201
        }

        then:
        within(stats, 2000)
        products.size() == PRODUCTS
    }

    def "a pipeline is created for every service"() {
        given:
        def serviceIds = products.collectMany { it.services*.id }

        when:
        def stats = LatencyStats.measure('Create a pipeline', calls: serviceIds.size(), threads: 8, warmUp: false) { int i ->
            def response = api.post("/api/services/${serviceIds[i]}/pipelines", [type: 'FULL', agentLabels: ['linux-agent']])
            if (response.status == 201) {
                pipelines << (response.json as Map)
            }
            response.status == 201
        }

        then:
        within(stats, 500)
        pipelines.size() == PRODUCTS * SERVICES
    }

    def "the product list and a product's details stay fast"() {
        when:
        def list = LatencyStats.measure('List all products', calls: 100, threads: 8) { api.get('/api/products').status == 200 }
        def details = LatencyStats.measure("Read a product with $SERVICES services", calls: 200, threads: 8) { int i ->
            api.get("/api/products/${products[i % PRODUCTS].id}").status == 200
        }
        def productPipelines = LatencyStats.measure("List a product's pipelines", calls: 200, threads: 8) { int i ->
            api.get("/api/products/${products[i % PRODUCTS].id}/pipelines").status == 200
        }

        then:
        within(list, 500)
        within(details, 300)
        within(productPipelines, 300)
    }

    def "a burst of pipelines fetching their configuration is served"() {
        given:
        def keys = pipelines*.activeKey*.value

        when:
        def stats = LatencyStats.measure('Fetch config.yaml by key, 32 at once', calls: 2000, threads: 32) { int i ->
            api.get("/api/dso/config/${keys[i % keys.size()]}").status == 200
        }

        then:
        within(stats, 300)
    }

    def "a burst of pipelines reading their configuration from the database view is served"() {
        given:
        def keys = pipelines*.activeKey*.value

        when:
        def stats = LatencyStats.measure('Read config from the DB view, 32 at once', calls: 2000, threads: 32) { int i ->
            libraryConfig(keys[i % keys.size()] as String).CONFIG_JSON != null
        }

        then:
        within(stats, 100)
    }

    def "a global settings change publishes the configuration of every pipeline"() {
        given:
        Map original = api.get('/api/settings').json as Map

        when:
        def stats = LatencyStats.measure("Change global settings, ${pipelines.size()} pipelines", calls: 5,
                warmUp: false) { int i ->
            def current = api.get('/api/settings').json
            api.put('/api/settings', original + [version: current.version,
                                                 scans  : original.scans + [coverageMinLine: 61 + i]]).status == 200
        }

        then:
        within(stats, 5000)
        libraryConfig(pipelines.last().activeKey.value as String).CONFIG_JSON.contains('"minLine":65')

        cleanup:
        def current = api.get('/api/settings').json
        api.put('/api/settings', original + [version: current.version])
    }

    def "the monitoring pages stay fast with every pipeline reporting"() {
        given:
        def now = Instant.now()
        lastRunsFinishedAt = now - Duration.ofHours(3)
        pipelines.each { influx.addRun(project: it.influxProjectTag, env: it.influxEnv, time: lastRunsFinishedAt) }
        def history = pipelines.first()
        (1..270).each { influx.addRun(project: history.influxProjectTag, env: history.influxEnv,
                time: now - Duration.ofHours(8 * it), result: it % 5 == 0 ? 'FAILURE' : 'SUCCESS') }

        when:
        def overview = LatencyStats.measure("Monitoring overview of ${pipelines.size()} pipelines", calls: 50, threads: 4) {
            def response = api.get('/api/monitoring/products')
            response.status == 200 && response.json.metricsError == null
        }
        def details = LatencyStats.measure('Pipeline details with 90 days of runs', calls: 100, threads: 8) {
            def response = api.get("/api/monitoring/pipelines/$history.id?range=90d")
            response.status == 200 && response.json.dora.runs == 270
        }

        then:
        within(overview, 1500)
        within(details, 800)
    }

    def "the change evidence of a product with 16 services stays fast"() {
        given:
        def evidenced = products.first()
        def finished = lastRunsFinishedAt
        pipelines.findAll { it.productId == evidenced.id }.each { pipeline ->
            def point = { Map args ->
                influx.addPoint([project: pipeline.influxProjectTag, env: pipeline.influxEnv, time: finished] + args)
            }
            point(measurement: 'code_coverage', module: pipeline.serviceName, line_pct: '82.5', required: '80', met: '1')
            ['smoke', 'regression', 'performance'].each { suite ->
                point(measurement: 'test_execution', module: pipeline.serviceName, suite: suite, total: '20', passed: '20',
                        failed: '0')
            }
            ['sast', 'dast'].each { scanner ->
                point(measurement: 'security_findings', module: pipeline.serviceName, scanner: scanner, critical: '0',
                        high: '1', medium: '2', low: '3', status: 'pass')
                point(measurement: 'policy_status', scanner: scanner, status: 'pass')
            }
            point(measurement: 'vulnerabilities', scanner: 'sonar', critical: '0', high: '0', medium: '1', low: '4')
            point(measurement: 'release_gate', allowed: 'yes', violations: '0')
            (1..12).each { stage ->
                point(measurement: 'stage_event', stage: "Stage $stage", status: 'pass', order: "$stage",
                        duration_s: '30', time: finished - Duration.ofSeconds(600 - 40 * stage))
            }
        }

        when:
        def stats = LatencyStats.measure("Change evidence of $SERVICES services", calls: 100, threads: 8) {
            def response = api.get("/api/evidence/products/$evidenced.id")
            response.status == 200 && response.json.metricsError == null &&
                    response.json.services.every { it.pipelines[0].run.stages.size() == 12 }
        }

        then:
        within(stats, 800)
    }

    def "concurrent key changes keep one active key per pipeline"() {
        given:
        def targets = pipelines.take(10)*.id

        when:
        def stats = LatencyStats.measure('Issue a new key, 10 at once', calls: 100, threads: 10) { int i ->
            api.post("/api/pipelines/${targets[i % targets.size()]}/keys").status == 200
        }

        then:
        within(stats, 1000)
        targets.every { id -> api.get("/api/pipelines/$id").json.keys.count { it.status == 'ACTIVE' } == 1 }
    }

    /** A Maven service with every section filled in, under metrics and SonarQube names of its own. */
    private static Map fullService(String code, String name) {
        String tag = "$code-$name"
        fullMavenService(name: name, metrics: [enabled: true, influxProject: tag, influxEnv: 'uat'],
                sonar: fullMavenService().sonar + [projectKey: tag], nexusIq: fullMavenService().nexusIq + [application: tag])
    }

    private boolean within(LatencyStats stats, double p95LimitMillis) {
        results << stats
        assert stats.errors == 0: "${stats.name}: ${stats.errors} failed calls"
        assert stats.p95 <= p95LimitMillis * FACTOR: "${stats.name}: p95 ${stats.p95} ms is over ${p95LimitMillis * FACTOR} ms"
        true
    }
}
