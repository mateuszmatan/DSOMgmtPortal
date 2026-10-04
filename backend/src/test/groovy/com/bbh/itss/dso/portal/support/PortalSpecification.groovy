package com.bbh.itss.dso.portal.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:dso-portal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'dso.demo-data=false',
        'dso.influx.token=test-token',
        'dso.grafana.url=http://grafana.test'])
@ActiveProfiles('local')
abstract class PortalSpecification extends Specification {

    private static final AtomicInteger SEQUENCE = new AtomicInteger()

    @Value('${local.server.port}')
    int port

    @Autowired
    JdbcTemplate jdbc

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

    static String uniqueCode(String prefix = 'REG') {
        "${prefix}${SEQUENCE.incrementAndGet()}${System.nanoTime() % 100_000}"
    }

    Map libraryConfig(String key) {
        jdbc.queryForMap('SELECT * FROM DSO_LIBRARY_CONFIG_V WHERE PIPELINE_KEY = ?', key)
                .collectEntries { name, value -> [name.toUpperCase(), value] }
    }

    Map createProduct(Map product) {
        def response = api.post('/api/products', product)
        assert response.status == 201: response
        response.json as Map
    }

    Map createPipeline(long serviceId, Map pipeline = ApiJson.pipeline()) {
        def response = api.post("/api/services/$serviceId/pipelines", pipeline)
        assert response.status == 201: response
        response.json as Map
    }
}
