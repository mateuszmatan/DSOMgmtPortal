package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.dsoconfig.PublishedConfig
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:published-config-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import([PublishedConfigPersistenceAdapter, PipelinePersistenceAdapter, ProductPersistenceAdapter])
class PublishedConfigPersistenceAdapterSpec extends Specification {

    static final Instant FIRST = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant LATER = Instant.parse('2026-10-04T12:00:00Z')

    @Subject
    @Autowired
    PublishedConfigPersistenceAdapter adapter

    @Autowired
    PipelinePersistenceAdapter pipelines

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    Pipeline pipeline

    def setup() {
        Product cert = products.save(Product.create(details(), account(),
                [new ServiceDraft(null, 'gui', null, settings())], products))
        pipeline = pipelines.save(Pipeline.create(new ServiceRef(cert.id(), cert.services()[0].id()), FULL,
                pipelineSettings(), { -> 'key-1' }, FIRST))
    }

    def "the first configuration of a pipeline is inserted, a later one replaces it, and the library view serves it"() {
        expect:
        adapter.load(pipeline.id()) == Optional.empty()

        when:
        publish('{"pipeline":{"type":"full"}}', FIRST)

        then:
        adapter.load(pipeline.id()).get() == new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"full"}}', FIRST)
        jdbc.queryForMap('SELECT KEY_STATUS, CONFIG_JSON FROM DSO_LIBRARY_CONFIG_V WHERE PIPELINE_KEY = ?', 'key-1')
                .collectEntries { name, value -> [name.toUpperCase(), value] } ==
                [KEY_STATUS: 'ACTIVE', CONFIG_JSON: '{"pipeline":{"type":"full"}}']

        when:
        publish('{"pipeline":{"type":"sast"}}', LATER)

        then:
        adapter.load(pipeline.id()).get() == new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"sast"}}', LATER)
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_CONFIG WHERE PIPELINE_ID = ?', Integer, pipeline.id()) == 1
    }

    def "deleting the pipeline removes its published configuration"() {
        given:
        publish('{}', FIRST)

        when:
        pipelines.delete(pipeline.id())
        entities.flush()

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_CONFIG', Integer) == 0
    }

    private void publish(String json, Instant renderedAt) {
        adapter.save(new PublishedConfig(pipeline.id(), json, renderedAt))
        entities.flush()
        entities.clear()
    }
}
