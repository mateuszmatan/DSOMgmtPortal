package com.bbh.itss.dso.portal.pipeline

import com.bbh.itss.dso.portal.catalog.MetricsSettings
import com.bbh.itss.dso.portal.common.ConflictException
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service

class PipelineSpec extends Specification {

    static final String UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/

    def gui = service(product(), name: 'gui')

    def "a new pipeline starts with one active key"() {
        when:
        def pipeline = pipeline(gui)

        then:
        pipeline.enabled
        pipeline.keys.size() == 1
        with(pipeline.activeKey().get()) {
            active
            status == KeyStatus.ACTIVE
            value ==~ UUID_PATTERN
            issuedAt != null
            revokedAt == null
            lastUsedAt == null
            it.pipeline.is(pipeline)
        }
    }

    def "every key is different"() {
        expect:
        (1..50).collect { pipeline(gui).activeKey().get().value }.toSet().size() == 50
    }

    def "issuing a key revokes the active one as replaced"() {
        given:
        def pipeline = pipeline(gui)
        def first = pipeline.activeKey().get()

        when:
        def second = pipeline.issueKey()

        then:
        pipeline.activeKey().get().is(second)
        pipeline.keys == [second, first]
        !first.active
        first.revokeReason == 'Replaced by a new key'
        first.revokedAt != null
        second.value != first.value
    }

    def "revoking the key disables the pipeline and keeps the reason"() {
        given:
        def pipeline = pipeline(gui)

        when:
        def revoked = pipeline.revokeActiveKey('  Service retired  ')

        then:
        !pipeline.enabled
        pipeline.activeKey().isEmpty()
        revoked.status == KeyStatus.REVOKED
        revoked.revokeReason == 'Service retired'
    }

    def "a pipeline without an active key has nothing to revoke"() {
        given:
        def pipeline = pipeline(gui)
        pipeline.revokeActiveKey('retired')

        when:
        pipeline.revokeActiveKey('again')

        then:
        def e = thrown(ConflictException)
        e.message == 'The pipeline has no active key to invalidate'
    }

    def "a disabled pipeline is enabled again with a new key"() {
        given:
        def pipeline = pipeline(gui)
        pipeline.revokeActiveKey('paused')

        when:
        pipeline.issueKey()

        then:
        pipeline.enabled
        pipeline.keys*.status == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
        pipeline.keys[1].revokeReason == 'paused'
    }

    def "using a key is recorded"() {
        given:
        def key = pipeline(gui).activeKey().get()

        when:
        key.markUsed()

        then:
        key.lastUsedAt != null
    }

    def "a key revoked without a reason has none"() {
        given:
        def key = pipeline(gui).activeKey().get()

        when:
        key.revoke(null)

        then:
        key.revokeReason == null
        new KeyRevokedException(key).message == "The DevSecOps pipeline key was invalidated on ${key.revokedAt}"
    }

    def "the key history cannot be changed past the pipeline"() {
        when:
        pipeline(gui).keys.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "only the security pipeline keeps the extended pipeline job"() {
        when:
        def pipeline = pipeline(gui, type: type, extendedPipelineJob: 'CERT/gui-extended', agentLabels: [' linux ', 'linux', 'docker'])

        then:
        pipeline.settings.extendedPipelineJob() == job
        pipeline.settings.agentLabels() == ['linux', 'docker']

        where:
        type                  || job
        PipelineType.SECURITY || 'CERT/gui-extended'
        PipelineType.FULL     || null
        PipelineType.EXTENDED || null
        PipelineType.SAST     || null
    }

    def "reconfiguring changes the settings but not the keys"() {
        given:
        def pipeline = pipeline(gui)
        def key = pipeline.activeKey().get()

        when:
        pipeline.configure(new PipelineSettings(['windows'], null, ' nightly '))

        then:
        pipeline.settings == new PipelineSettings(['windows'], null, 'nightly')
        pipeline.activeKey().get().is(key)
    }

    def "the #type pipeline reads its metrics from project tag #tag"() {
        given:
        def guiService = service(product(), name: 'gui', metrics: new MetricsSettings(true, 'cert-gui', 'uat'))
        def created = pipeline(guiService, type: type)

        expect:
        created.influxProjectTag() == tag
        created.influxEnv() == 'uat'
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
