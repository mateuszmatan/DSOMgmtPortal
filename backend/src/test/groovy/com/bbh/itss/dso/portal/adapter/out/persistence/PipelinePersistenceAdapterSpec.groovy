package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:pipeline-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import([PipelinePersistenceAdapter, PipelineMapper, ProductPersistenceAdapter, ProductMapper])
class PipelinePersistenceAdapterSpec extends Specification {

    static final Instant ISSUED = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant REISSUED = Instant.parse('2026-10-02T09:30:00Z')
    static final Instant USED = Instant.parse('2026-10-03T10:15:00Z')

    @Subject
    @Autowired
    PipelinePersistenceAdapter adapter

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    Keys generator = new Keys()

    Product cert

    def setup() {
        cert = products.save(Product.create(details(), account(), [draft('gui'), draft('api')], products))
    }

    def "a new pipeline is stored with its settings and its first active key"() {
        when:
        def saved = adapter.save(Pipeline.create(ref(cert, 0), FULL,
                pipelineSettings(agentLabels: ['linux', 'docker'], jenkinsJob: 'CERT/gui', description: 'Main',
                        extendedPipelineJob: 'ignored'), generator, ISSUED))
        entities.clear()
        def loaded = adapter.load(saved.id()).get()

        then:
        saved.id() != null
        saved.version() == 0
        saved.createdAt() != null
        loaded.service() == ref(cert, 0)
        loaded.type() == FULL
        loaded.settings() == pipelineSettings(agentLabels: ['linux', 'docker'], jenkinsJob: 'CERT/gui',
                description: 'Main')
        loaded.keys().size() == 1
        with(loaded.keys()[0]) {
            id() == saved.keys()[0].id()
            value() == 'key-1'
            status() == KeyStatus.ACTIVE
            issuedAt() == ISSUED
            revokedAt() == null
            revokeReason() == null
            lastUsedAt() == null
        }
        jdbc.queryForMap('SELECT PIPELINE_TYPE, AGENT_LABELS, EXTENDED_PIPELINE_JOB, JENKINS_JOB FROM DSO_PIPELINE') ==
                [PIPELINE_TYPE: 'FULL', AGENT_LABELS: 'linux,docker', EXTENDED_PIPELINE_JOB: null,
                 JENKINS_JOB: 'CERT/gui']
    }

    def "a pipeline needs a stored service"() {
        when:
        adapter.save(Pipeline.create(new ServiceRef(cert.id(), 999_999L), FULL, pipelineSettings(), generator, ISSUED))

        then:
        def problem = thrown(NotFoundException)
        problem.message.contains('999999')
    }

    def "a reconfigured pipeline keeps its keys"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), SECURITY, pipelineSettings(), generator, ISSUED))
        entities.clear()
        def pipeline = adapter.load(stored.id()).get()

        when:
        pipeline.reconfigure(SECURITY, pipelineSettings(agentLabels: ['windows'], extendedPipelineJob: 'CERT/extended'))
        def saved = adapter.save(pipeline)
        entities.clear()

        then:
        def loaded = adapter.load(stored.id()).get()
        saved.version() == stored.version() + 1
        loaded.settings().agentLabels() == ['windows']
        loaded.settings().extendedPipelineJob() == 'CERT/extended'
        loaded.keys()*.id() == stored.keys()*.id()
    }

    def "a new key replaces the active key, which stays in the history as revoked"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        entities.clear()
        def pipeline = adapter.loadForUpdate(stored.id()).get()

        when:
        pipeline.issueKey(generator, REISSUED)
        def saved = adapter.save(pipeline)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        saved.keys()*.value() == ['key-2', 'key-1']
        loaded.keys()*.value() == ['key-2', 'key-1']
        loaded.keys()*.status() == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
        loaded.keys()[1].id() == stored.keys()[0].id()
        loaded.keys()[1].revokedAt() == REISSUED
        loaded.keys()[1].revokeReason() == Pipeline.REPLACED_REASON
        loaded.keys()[0].issuedAt() == REISSUED
        loaded.activeKey().get().value() == 'key-2'
    }

    def "a revoked key disables the pipeline"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        entities.clear()
        def pipeline = adapter.loadForUpdate(stored.id()).get()

        when:
        pipeline.revokeActiveKey('Service retired', REISSUED)
        adapter.save(pipeline)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        !loaded.enabled
        loaded.keys()[0].status() == KeyStatus.REVOKED
        loaded.keys()[0].revokeReason() == 'Service retired'
        loaded.keys()[0].revokedAt() == REISSUED
    }

    def "any key of a pipeline is found with the pipeline it was issued for"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def pipeline = adapter.load(stored.id()).get()
        pipeline.issueKey(generator, REISSUED)
        def saved = adapter.save(pipeline)
        entities.clear()

        when:
        def replaced = adapter.findKey('key-1').get()
        def active = adapter.findKey('key-2').get()

        then:
        replaced.pipelineId() == stored.id()
        replaced.key() == saved.keys()[1]
        replaced.key().status() == KeyStatus.REVOKED
        active.pipelineId() == stored.id()
        active.key() == saved.keys()[0]
        active.key().status() == KeyStatus.ACTIVE
        adapter.findKey('key-3').empty
        adapter.load(999_999L).empty
        adapter.loadForUpdate(999_999L).empty
    }

    def "a key use is recorded on active keys only and never changes their status"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def pipeline = adapter.load(stored.id()).get()
        pipeline.issueKey(generator, REISSUED)
        def saved = adapter.save(pipeline)
        def (active, revoked) = saved.keys()*.id()

        when:
        def recordedActive = adapter.recordKeyUse(active, USED)
        def recordedRevoked = adapter.recordKeyUse(revoked, USED)
        def recordedMissing = adapter.recordKeyUse(999_999L, USED)
        entities.clear()
        def loaded = adapter.load(stored.id()).get()

        then:
        recordedActive
        !recordedRevoked
        !recordedMissing
        loaded.keys()*.lastUsedAt() == [USED, null]
        loaded.keys()*.status() == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
        loaded.version() == saved.version()
    }

    def "a pipeline never holds a second active key"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        entities.flush()

        when:
        jdbc.update("""INSERT INTO DSO_PIPELINE_KEY (PIPELINE_ID, KEY_VALUE, STATUS, ISSUED_AT)
                       VALUES (?, 'key-second', 'ACTIVE', CURRENT_TIMESTAMP)""", stored.id())

        then:
        thrown(DataIntegrityViolationException)
    }

    def "a pipeline keeps any number of revoked keys beside its active key"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def pipeline = adapter.load(stored.id()).get()
        pipeline.issueKey(generator, REISSUED)
        pipeline.issueKey(generator, USED)
        adapter.save(pipeline)
        pipeline = adapter.load(stored.id()).get()
        pipeline.revokeActiveKey('Service retired', USED)
        adapter.save(pipeline)
        pipeline = adapter.load(stored.id()).get()
        pipeline.issueKey(generator, USED)
        adapter.save(pipeline)
        entities.clear()

        expect:
        adapter.load(stored.id()).get().keys()*.status().countBy { it } ==
                [(KeyStatus.ACTIVE): 1, (KeyStatus.REVOKED): 3]
        jdbc.queryForObject("SELECT COUNT(*) FROM DSO_PIPELINE_KEY WHERE STATUS = 'ACTIVE'", Integer) == 1
    }

    def "pipelines are listed per product and overall in service and type order"() {
        given:
        def other = products.save(Product.create(details(code: 'ABC', name: 'Abacus'), account(), [draft('core')],
                products))
        def apiSast = adapter.save(Pipeline.create(ref(cert, 1), SAST, pipelineSettings(), generator, ISSUED))
        def guiSecurity = adapter.save(Pipeline.create(ref(cert, 0), SECURITY, pipelineSettings(), generator, ISSUED))
        def guiFull = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def core = adapter.save(Pipeline.create(ref(other, 0), FULL, pipelineSettings(), generator, ISSUED))
        entities.clear()

        expect:
        adapter.findByProductId(cert.id())*.id() == [guiFull.id(), guiSecurity.id(), apiSast.id()]
        adapter.findByProductId(other.id())*.id() == [core.id()]
        adapter.findByProductId(999_999L).empty
        adapter.findAll()*.id() == [core.id(), guiFull.id(), guiSecurity.id(), apiSast.id()]
        adapter.findAll()*.service() == [ref(other, 0), ref(cert, 0), ref(cert, 0), ref(cert, 1)]
    }

    def "a service has at most one pipeline of each type"() {
        given:
        adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def gui = cert.services()[0].id()
        def api = cert.services()[1].id()

        expect:
        adapter.existsForService(gui, FULL)
        !adapter.existsForService(gui, SAST)
        !adapter.existsForService(api, FULL)
    }

    def "the counts per product tell all pipelines from those with an active key"() {
        given:
        def other = products.save(Product.create(details(code: 'ABC', name: 'Abacus'), account(), [draft('core')],
                products))
        adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def revoked = adapter.save(Pipeline.create(ref(cert, 1), SAST, pipelineSettings(), generator, ISSUED))
        def reissued = adapter.save(Pipeline.create(ref(other, 0), FULL, pipelineSettings(), generator, ISSUED))
        revoked.revokeActiveKey('Retired', REISSUED)
        adapter.save(revoked)
        reissued.issueKey(generator, REISSUED)
        adapter.save(reissued)
        entities.clear()

        expect:
        adapter.pipelinesPerProduct() == [(cert.id()): 2L, (other.id()): 1L]
        adapter.activePipelinesPerProduct() == [(cert.id()): 1L, (other.id()): 1L]
    }

    def "a pipeline read at another version than the stored one is not written"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        def stale = Pipeline.restore(stored.id(), stored.service(), FULL, pipelineSettings(agentLabels: ['stale']),
                stored.keys(), stored.version() + 1, stored.createdAt(), stored.updatedAt())

        when:
        adapter.save(stale)

        then:
        thrown(ConflictException)
        adapter.load(stored.id()).get().settings() == pipelineSettings()
    }

    def "a pipeline deleted in the meantime cannot be written"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        adapter.delete(stored.id())

        when:
        adapter.save(stored)

        then:
        thrown(ConflictException)
    }

    def "deleting a pipeline removes its keys"() {
        given:
        def stored = adapter.save(Pipeline.create(ref(cert, 0), FULL, pipelineSettings(), generator, ISSUED))
        stored.issueKey(generator, REISSUED)
        adapter.save(stored)

        when:
        adapter.delete(stored.id())
        adapter.delete(999_999L)
        entities.flush()

        then:
        adapter.load(stored.id()).empty
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE', Integer) == 0
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PIPELINE_KEY', Integer) == 0
    }

    private static ServiceDraft draft(String name) {
        new ServiceDraft(null, name, null, settings())
    }

    private static ServiceRef ref(Product product, int service) {
        new ServiceRef(product.id(), product.services()[service].id())
    }

    static class Keys implements KeyGenerator {

        int issued

        @Override
        String newKey() {
            "key-${++issued}"
        }
    }
}
