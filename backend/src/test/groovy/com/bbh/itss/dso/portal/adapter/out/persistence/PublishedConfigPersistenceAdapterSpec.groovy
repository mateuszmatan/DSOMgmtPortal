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

    def "a pipeline without a published configuration has none to load"() {
        expect:
        adapter.load(pipeline.id()) == Optional.empty()
    }

    def "the first configuration of a pipeline is inserted and read back"() {
        when:
        adapter.save(new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"full"}}', FIRST))
        entities.flush()
        entities.clear()

        then:
        adapter.load(pipeline.id()).get() == new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"full"}}', FIRST)
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_CONFIG WHERE PIPELINE_ID = ?', Integer, pipeline.id()) == 1
    }

    def "a later configuration replaces the stored one in the same row"() {
        given:
        adapter.save(new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"full"}}', FIRST))
        entities.flush()
        entities.clear()

        when:
        adapter.save(new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"sast"}}', LATER))
        entities.flush()
        entities.clear()

        then:
        adapter.load(pipeline.id()).get() == new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"sast"}}', LATER)
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_CONFIG WHERE PIPELINE_ID = ?', Integer, pipeline.id()) == 1
    }

    def "the library view serves the published configuration under the active key"() {
        given:
        adapter.save(new PublishedConfig(pipeline.id(), '{"pipeline":{"type":"full"}}', FIRST))
        entities.flush()

        expect:
        jdbc.queryForMap('SELECT KEY_STATUS, CONFIG_JSON FROM DSO_LIBRARY_CONFIG_V WHERE PIPELINE_KEY = ?', 'key-1')
                .collectEntries { name, value -> [name.toUpperCase(), value] } ==
                [KEY_STATUS: 'ACTIVE', CONFIG_JSON: '{"pipeline":{"type":"full"}}']
    }

    def "deleting the pipeline removes its published configuration"() {
        given:
        adapter.save(new PublishedConfig(pipeline.id(), '{}', FIRST))
        entities.flush()
        entities.clear()

        when:
        pipelines.delete(pipeline.id())
        entities.flush()

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_CONFIG', Integer) == 0
    }
}
