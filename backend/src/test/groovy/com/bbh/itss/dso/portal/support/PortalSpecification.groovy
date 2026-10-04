package com.bbh.itss.dso.portal.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * Starts the whole portal on a random port with the local profile on an in-memory H2 database, InfluxDB
 * replaced by {@link FakeInfluxDb} and Grafana links pointing at a test URL. Specs talk to it over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:dso-portal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'dso.demo-data=false',
        'dso.influx.token=test-token',
        'dso.grafana.url=http://grafana.test',
        // The documented BBH defaults, so environment variables of a build agent cannot change the rendered config.
        'dso.defaults.asoc-url=https://bbh.cloud.appscan.com',
        'dso.defaults.sonar-server-url=https://tools.bbh.com/sonar',
        'dso.defaults.nexus-iq-server-url=https://tools.bbh.com/IQ',
        'dso.defaults.nexus-iq-credentials-id=nexusiqP',
        'dso.defaults.influx-write-url=http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s',
        'dso.defaults.influx-credentials-id=influxdb-token'])
@ActiveProfiles('local')
abstract class PortalSpecification extends Specification {

    private static final AtomicInteger SEQUENCE = new AtomicInteger()

    @Value('${local.server.port}')
    int port

    FakeInfluxDb getInflux() {
        FakeInfluxDb.shared()
    }

    @DynamicPropertySource
    static void influxUrl(DynamicPropertyRegistry registry) {
        registry.add('dso.influx.url') { FakeInfluxDb.shared().url }
    }

    PortalClient getApi() {
        new PortalClient("http://localhost:$port")
    }

    /** A product code no other spec uses, since all specs share the database. */
    static String uniqueCode(String prefix = 'REG') {
        "${prefix}${SEQUENCE.incrementAndGet()}${System.nanoTime() % 100_000}"
    }

    /** Creates a product and returns its JSON. */
    Map createProduct(Map product) {
        def response = api.post('/api/products', product)
        assert response.status == 201: response
        response.json as Map
    }

    /** Creates a pipeline for a service and returns its JSON. */
    Map createPipeline(long serviceId, Map pipeline = ApiJson.pipeline()) {
        def response = api.post("/api/services/$serviceId/pipelines", pipeline)
        assert response.status == 201: response
        response.json as Map
    }
}
