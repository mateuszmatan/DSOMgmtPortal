package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.KEY
import static com.bbh.itss.dso.portal.support.Fixtures.activeKey
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.settings

class PipelineResponseSpec extends Specification {

    def certScanner = product(id: 1, code: 'CERT', name: 'CertScanner',
            services: [[name: 'gui', id: 10, settings: settings(metrics: new MetricsSettings(true, 'cert-gui', 'uat'))]])

    def "a summary links the pipeline's job path under the Jenkins URL and leaves out the key history"() {
        given:
        def security = pipeline(id: 100, type: PipelineType.SECURITY, agentLabels: ['linux', 'docker'],
                extendedPipelineJob: 'CERT/gui-extended', jenkinsJob: 'DevSecOps/CERT/gui-security',
                description: 'Nightly scan', keys: [activeKey(), revokedKey()])

        when:
        def response = PipelineResponse.summary(PipelineView.of(certScanner, security, 'https://jenkins.test/'))

        then:
        with(response) {
            id() == 100
            productId() == 1
            productCode() == 'CERT'
            productName() == 'CertScanner'
            serviceId() == 10
            serviceName() == 'gui'
            type() == PipelineType.SECURITY
            entryPoint() == 'devSecOpsSecurityPipeline'
            agentLabels() == ['linux', 'docker']
            extendedPipelineJob() == 'CERT/gui-extended'
            securityPipelineJob() == null
            jenkinsJob() == 'DevSecOps/CERT/gui-security'
            jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-security/'
            description() == 'Nightly scan'
            enabled()
            activeKey().value() == KEY
            activeKey().hint() == '0f8fad5b\u2026950e'
            activeKey().status() == KeyStatus.ACTIVE
            influxProjectTag() == 'cert-guisecurity'
            influxEnv() == 'uat'
            createdAt() != null
            updatedAt() != null
            keys() == null
        }
    }

    def "the full response lists every key by its hint, newest first, and only the active key's value"() {
        given:
        def full = pipeline(id: 100, jenkinsJob: 'https://jenkins.bbh.com/job/CERT/job/gui/',
                keys: [activeKey(lastUsedAt: certScanner.updatedAt()), revokedKey()])

        when:
        def response = PipelineResponse.withKeys(PipelineView.of(certScanner, full, 'https://jenkins.test'))

        then:
        response.jenkinsJobUrl() == 'https://jenkins.bbh.com/job/CERT/job/gui/'
        response.keys()*.id() == [100L, 99L]
        response.keys()*.status() == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
        response.keys()*.value() == [KEY, null]
        response.keys()*.hint() == ['0f8fad5b\u2026950e', '6ba7b810\u202630c8']
        response.keys()[0].lastUsedAt() == certScanner.updatedAt()
        response.keys()[1].revokeReason() == 'Replaced by a new key'
        response.keys()[1].revokedAt() != null
        response.keys()[1].issuedAt() != null
    }

    def "a job path without a Jenkins URL has no link, and a disabled pipeline no active key"() {
        given:
        def sast = pipeline(id: 101, type: PipelineType.SAST, jenkinsJob: 'DevSecOps/CERT/gui-sast', keys: [revokedKey()])

        when:
        def response = PipelineResponse.summary(PipelineView.of(certScanner, sast, null))

        then:
        response.jenkinsJob() == 'DevSecOps/CERT/gui-sast'
        response.jenkinsJobUrl() == null
        !response.enabled()
        response.activeKey() == null
    }

    def "a monitored pipeline shows its active key by the hint alone and no key history"() {
        given:
        def full = pipeline(id: 100, keys: [activeKey(), revokedKey()])

        when:
        def response = PipelineResponse.monitored(PipelineView.of(certScanner, full, null))

        then:
        response.enabled()
        response.activeKey().value() == null
        response.activeKey().hint() == '0f8fad5b\u2026950e'
        response.activeKey().status() == KeyStatus.ACTIVE
        response.keys() == []
    }

    def "a service is listed with its build tool, deployment target and pipeline summaries"() {
        given:
        def view = PipelineView.of(certScanner, pipeline(id: 100), null)

        when:
        def response = PipelineController.ServicePipelinesResponse.of(new ServicePipelinesView(certScanner.services()[0], [view]))

        then:
        response.serviceId() == 10
        response.serviceName() == 'gui'
        response.description() == null
        response.buildTool() == BuildTool.GRADLE
        response.deployTarget() == DeployTarget.VM
        response.pipelines()*.id() == [100L]
        response.pipelines()[0].keys() == null
    }
}
