package com.bbh.dso.portal.performance

import com.bbh.dso.portal.support.FakeInfluxDb
import com.bbh.dso.portal.support.LatencyStats
import com.bbh.dso.portal.support.PortalSpecification
import spock.lang.Shared
import spock.lang.Stepwise

import java.time.Duration
import java.time.Instant

import static com.bbh.dso.portal.support.ApiJson.product
import static com.bbh.dso.portal.support.ApiJson.service

/**
 * Response times under a realistic load: 25 products of 16 services, a pipeline for every service and bursts
 * of configuration requests as when many Jenkins jobs start at once. The limits are generous so the suite
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
                    services: (1..SERVICES).collect { service(name: "service-$it") }))
            if (response.status == 201) {
                products << (response.json as Map)
            }
            response.status == 201
        }

        then:
        within(stats, 1500)
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

    def "the monitoring pages stay fast with every pipeline reporting"() {
        given:
        def now = Instant.now()
        pipelines.each { influx.addRun(project: it.influxProjectTag, time: now - Duration.ofHours(3)) }
        def history = pipelines.first()
        (1..270).each { influx.addRun(project: history.influxProjectTag, time: now - Duration.ofHours(8 * it),
                result: it % 5 == 0 ? 'FAILURE' : 'SUCCESS') }

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

    private boolean within(LatencyStats stats, double p95LimitMillis) {
        results << stats
        assert stats.errors == 0: "${stats.name}: ${stats.errors} failed calls"
        assert stats.p95 <= p95LimitMillis * FACTOR: "${stats.name}: p95 ${stats.p95} ms is over ${p95LimitMillis * FACTOR} ms"
        true
    }
}
