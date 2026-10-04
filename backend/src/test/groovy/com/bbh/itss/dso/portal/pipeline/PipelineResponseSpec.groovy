package com.bbh.itss.dso.portal.pipeline

import com.bbh.itss.dso.portal.catalog.MetricsSettings
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service

class PipelineResponseSpec extends Specification {

    def certScanner = product(id: 1, code: 'CERT', name: 'CertScanner')
    def gui = service(certScanner, name: 'gui', id: 10, metrics: new MetricsSettings(true, 'cert-gui', 'uat'))

    def "a summary links the pipeline's job path under the Jenkins URL and leaves out the key history"() {
        given:
        def security = pipeline(gui, id: 100, type: PipelineType.SECURITY, agentLabels: ['linux', 'docker'],
                extendedPipelineJob: 'CERT/gui-extended', jenkinsJob: 'DevSecOps/CERT/gui-security',
                description: 'Nightly scan')
        security.issueKey()

        when:
        def response = PipelineResponse.summary(security, 'https://jenkins.test/')

        then:
        with(response) {
            id == 100
            productId == 1
            productCode == 'CERT'
            productName == 'CertScanner'
            serviceId == 10
            serviceName == 'gui'
            type == PipelineType.SECURITY
            entryPoint == 'devSecOpsSecurityPipeline'
            agentLabels == ['linux', 'docker']
            extendedPipelineJob == 'CERT/gui-extended'
            securityPipelineJob == null
            jenkinsJob == 'DevSecOps/CERT/gui-security'
            jenkinsJobUrl == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-security/'
            description == 'Nightly scan'
            enabled
            activeKey.value == security.activeKey().get().value
            activeKey.status == KeyStatus.ACTIVE
            influxProjectTag == 'cert-guisecurity'
            influxEnv == 'uat'
            keys == null
        }
    }

    def "the full response lists every key, newest first"() {
        given:
        def full = pipeline(gui, id: 100, jenkinsJob: 'https://jenkins.bbh.com/job/CERT/job/gui/')
        def first = full.activeKey().get()
        def second = full.issueKey()

        when:
        def response = PipelineResponse.withKeys(full, 'https://jenkins.test')

        then:
        response.jenkinsJobUrl == 'https://jenkins.bbh.com/job/CERT/job/gui/'
        response.keys*.value == [second.value, first.value]
        response.keys*.status == [KeyStatus.ACTIVE, KeyStatus.REVOKED]
        response.keys[1].revokeReason == 'Replaced by a new key'
    }

    def "a job path without a Jenkins URL has no link, and a disabled pipeline no active key"() {
        given:
        def sast = pipeline(gui, id: 101, type: PipelineType.SAST, jenkinsJob: 'DevSecOps/CERT/gui-sast')
        sast.revokeActiveKey('retired')

        when:
        def response = PipelineResponse.summary(sast, null)

        then:
        response.jenkinsJob == 'DevSecOps/CERT/gui-sast'
        response.jenkinsJobUrl == null
        !response.enabled
        response.activeKey == null
    }
}
