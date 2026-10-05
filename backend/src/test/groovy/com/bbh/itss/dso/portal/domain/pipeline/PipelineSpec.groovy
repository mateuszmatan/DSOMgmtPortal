package com.bbh.itss.dso.portal.domain.pipeline

import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.Fixtures.activeKey
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.pipelineSettings
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey

class PipelineSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-05T10:15:30.123456Z')
    static final Instant LATER = Instant.parse('2026-10-05T11:00:00Z')
    static final ServiceRef GUI = new ServiceRef(1, 10)

    int issued
    KeyGenerator generator = { -> "key-${++issued}".toString() } as KeyGenerator

    def "a new pipeline of a service starts with one active key"() {
        when:
        def created = Pipeline.create(GUI, PipelineType.FULL, pipelineSettings(), generator, NOW)

        then:
        created.id() == null
        created.service() == GUI
        created.type() == PipelineType.FULL
        created.version() == 0
        created.createdAt() == null
        created.updatedAt() == null
        created.enabled
        created.keys() == [new PipelineKey(null, 'key-1', KeyStatus.ACTIVE, NOW, null, null, null)]
        created.activeKey().get().active
        pipeline(id: 20, productId: 3, serviceId: 30, version: 7).with { [id(), service(), version(), createdAt(), updatedAt()] } ==
                [20L, new ServiceRef(3, 30), 7L, Instant.parse('2026-10-01T08:00:00Z'), Instant.parse('2026-10-02T09:30:00Z')]
    }

    def "issuing a key revokes the active one as replaced"() {
        given:
        def pipeline = Pipeline.create(GUI, PipelineType.FULL, pipelineSettings(), generator, NOW)

        when:
        def second = pipeline.issueKey(generator, LATER)

        then:
        second == new PipelineKey(null, 'key-2', KeyStatus.ACTIVE, LATER, null, null, null)
        pipeline.activeKey().get() == second
        pipeline.keys()*.value() == ['key-2', 'key-1']
        pipeline.keys()[1] == new PipelineKey(null, 'key-1', KeyStatus.REVOKED, NOW, LATER, Pipeline.REPLACED_REASON, null)
        Pipeline.REPLACED_REASON == 'Replaced by a new key'
    }

    def "revoking the key disables the pipeline and keeps the reason"() {
        given:
        def pipeline = pipeline(keys: [activeKey(), revokedKey()])

        when:
        def revoked = pipeline.revokeActiveKey('  Service retired  ', LATER)

        then:
        !pipeline.enabled
        pipeline.activeKey().isEmpty()
        revoked.status() == KeyStatus.REVOKED
        revoked.revokeReason() == 'Service retired'
        revoked.revokedAt() == LATER
        revoked.id() == 100L
        pipeline.keys() == [revoked, revokedKey()]
    }

    def "a disabled pipeline is enabled again with a new key"() {
        given:
        def pipeline = pipeline(keys: [revokedKey(reason: 'paused')])

        when:
        pipeline.issueKey(generator, LATER)

        then:
        pipeline.enabled
        pipeline.keys()*.status() == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
        pipeline.keys()[1].revokeReason() == 'paused'
        pipeline.keys()[1].revokedAt() == revokedKey().revokedAt()
    }

    def "a key revoked without a reason is refused without one"() {
        given:
        def revoked = revokedKey(reason: null)

        expect:
        new KeyRevokedException(revoked).message == "The DevSecOps pipeline key was invalidated on ${revoked.revokedAt()}"
    }

    def "#refusal is refused"() {
        when:
        action()

        then:
        def e = thrown(failure)
        e.message == message

        where:
        refusal                         | action                                                             || failure                       | message
        'revoking without an active key' | { pipeline(keys: [revokedKey()]).revokeActiveKey('again', LATER) } || ConflictException             | 'The pipeline has no active key to invalidate'
        'a second active key'           | { pipeline(keys: [activeKey(), activeKey(id: 101, value: 'x')]) }  || IllegalArgumentException      | 'a pipeline has at most one active key'
        'changing the key history'      | { pipeline().keys().clear() }                                      || UnsupportedOperationException | null
        'changing the type'             | { pipeline().reconfigure(PipelineType.SAST, pipelineSettings()) }  || ConflictException             | 'The type of a pipeline cannot change; add a new pipeline instead'
    }

    def "only the security pipeline keeps the extended pipeline job and only the extended one the security job"() {
        when:
        def created = Pipeline.create(GUI, type, pipelineSettings(extendedPipelineJob: 'CERT/gui-extended',
                securityPipelineJob: 'CERT/gui-security', agentLabels: [' linux ', 'linux', 'docker']), generator, NOW)

        then:
        created.settings().extendedPipelineJob() == extended
        created.settings().securityPipelineJob() == security
        created.settings().agentLabels() == ['linux', 'docker']

        where:
        type                  || extended            | security
        PipelineType.SECURITY || 'CERT/gui-extended' | null
        PipelineType.EXTENDED || null                | 'CERT/gui-security'
        PipelineType.FULL     || null                | null
        PipelineType.SAST     || null                | null
    }

    def "reconfiguring changes the settings but not the keys"() {
        given:
        def pipeline = pipeline(type: PipelineType.SECURITY)
        def keys = pipeline.keys()

        when:
        pipeline.reconfigure(PipelineType.SECURITY, new PipelineSettings(['windows'], ' CERT/ext ', 'CERT/sec', ' CERT/gui ',
                ' nightly '))

        then:
        pipeline.settings() == new PipelineSettings(['windows'], 'CERT/ext', null, 'CERT/gui', 'nightly')
        pipeline.keys() == keys
    }

    def "agent labels that do not fit their column are refused when a pipeline is created or reconfigured"() {
        given:
        def labels = (1..20).collect { "agent-$it-${'x' * 50}".toString() }
        def tooMany = new PipelineSettings(labels, null, null, null, null)
        def existing = pipeline()

        when:
        Pipeline.create(GUI, PipelineType.FULL, tooMany, generator, NOW)

        then:
        def created = thrown(InvalidRequestException)
        created.problems*.field() == ['agentLabels']

        when:
        existing.reconfigure(existing.type(), tooMany)

        then:
        def reconfigured = thrown(InvalidRequestException)
        reconfigured.problems*.message() == ['is too long: all entries together may take at most 1000 bytes']
        existing.settings() != tooMany
    }

    def "the #type pipeline reads its metrics from project tag #tag"() {
        expect:
        type.influxProjectTag('cert-gui') == tag

        where:
        type                  || tag
        PipelineType.FULL     || 'cert-gui'
        PipelineType.SECURITY || 'cert-guisecurity'
        PipelineType.EXTENDED || 'cert-guiextended'
        PipelineType.SAST     || 'cert-guisast'
    }

    def "each type names the library entry point it runs"() {
        expect:
        PipelineType.values().collect { [it.entryPoint(), it.variant()] } == [
                ['devSecOpsPipeline', 'full'], ['devSecOpsSecurityPipeline', 'security'],
                ['devSecOpsExtendedPipeline', 'extended'], ['devSecOpsSASTScanningPipeline', 'sast']]
    }
}
