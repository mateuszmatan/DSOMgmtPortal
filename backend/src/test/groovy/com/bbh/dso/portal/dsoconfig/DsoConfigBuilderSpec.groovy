package com.bbh.dso.portal.dsoconfig

import com.bbh.dso.portal.catalog.AdditionalConfig
import com.bbh.dso.portal.catalog.BuildTool
import com.bbh.dso.portal.catalog.DeployTarget
import com.bbh.dso.portal.catalog.NexusIqSettings
import com.bbh.dso.portal.catalog.SonarSettings
import com.bbh.dso.portal.pipeline.PipelineType
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.dso.portal.support.Fixtures.build
import static com.bbh.dso.portal.support.Fixtures.deployment
import static com.bbh.dso.portal.support.Fixtures.pipeline
import static com.bbh.dso.portal.support.Fixtures.product
import static com.bbh.dso.portal.support.Fixtures.service

class DsoConfigBuilderSpec extends Specification {

    static final DsoDefaultsProperties DEFAULTS = new DsoDefaultsProperties('https://asoc.test', 'https://sonar.test',
            'https://iq.test', 'nexusiq-creds', 'http://influx.test/api/v2/write', 'influx-creds')

    @Subject
    def builder = new DsoConfigBuilder(DEFAULTS)

    def certScanner = product()
    def gui = service(certScanner, name: 'gui', sonar: new SonarSettings('CertScanner GUI', 'cert-gui'),
            nexusIq: new NexusIqSettings('cert-gui', ['**/build/libs/*.jar']))
    def api = service(certScanner, name: 'backend-api', build: build(tool: BuildTool.MAVEN),
            deployment: deployment(target: DeployTarget.OPENSHIFT, appName: 'cert-api', artifactName: 'cert-api.jar'))

    def "a product's config lists every service under projects in the template's key order"() {
        when:
        Map projects = builder.productConfig(certScanner).projects

        then:
        projects.keySet() as List == ['gui', 'backend-api']
        projects.gui.keySet() as List == ['appId', 'buildTool', 'deployTarget', 'sourceDir', 'javaPath', 'asoc', 'influx',
                                          'tools', 'dast', 'goldenFix']
        projects['backend-api'].appName == 'cert-api'
        projects['backend-api'].buildTool == 'maven'
    }

    def "every service gets the BBH-wide defaults next to its own values"() {
        when:
        Map entry = builder.productConfig(certScanner).projects.gui

        then:
        entry.asoc == [url: 'https://asoc.test', keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        entry.influx == [url: 'http://influx.test/api/v2/write', credentialsId: 'influx-creds', enabled: true,
                         project: 'CERT-gui', env: 'test']
        entry.tools == [sonar  : [serverUrl: 'https://sonar.test', projectName: 'CertScanner GUI', projectKey: 'cert-gui'],
                        nexusIq: [serverUrl: 'https://iq.test', credentialsId: 'nexusiq-creds', application: 'cert-gui',
                                  scanPatterns: ['**/build/libs/*.jar']]]
    }

    def "the additional YAML overrides the defaults and the dedicated fields override the YAML"() {
        given:
        def batch = service(certScanner, name: 'batch', additionalConfig: new AdditionalConfig(
                'asoc:\n  url: https://asoc.other\nbuildTool: maven\ntests:\n  smoke:\n    enabled: true'))

        when:
        Map entry = builder.productConfig(certScanner).projects.batch

        then:
        entry.asoc.url == 'https://asoc.other'
        entry.buildTool == 'gradle'
        entry.tests == [smoke: [enabled: true]]
        entry.keySet().toList().indexOf('tests') > entry.keySet().toList().indexOf('goldenFix')
    }

    def "a pipeline receives its Jenkinsfile settings and only its own service"() {
        when:
        def config = builder.pipelineConfig(pipeline(gui, agentLabels: ['linux-agent', 'docker']))

        then:
        config.keySet() as List == ['pipeline', 'projects']
        config.pipeline == [type: 'full', entryPoint: 'devSecOpsPipeline', product: 'CERT', projectNames: 'gui',
                            agentNames: ['linux-agent', 'docker']]
        config.projects.keySet() as List == ['gui']
        !config.projects.gui.containsKey('jenkins')
    }

    def "the security pipeline names the extended pipeline job it starts"() {
        when:
        def config = builder.pipelineConfig(pipeline(gui, type: PipelineType.SECURITY, extendedPipelineJob: 'CERT/gui-extended'))

        then:
        config.pipeline.type == 'security'
        config.pipeline.entryPoint == 'devSecOpsSecurityPipeline'
        config.projects.gui.jenkins == [pipeline: [extendedPipeline: 'CERT/gui-extended']]
    }

    def "a security pipeline without an extended job writes no jenkins section"() {
        expect:
        !builder.pipelineConfig(pipeline(gui, type: PipelineType.SECURITY)).projects.gui.containsKey('jenkins')
    }

    def "the YAML uses block style and reads back to the same config"() {
        given:
        def config = builder.pipelineConfig(pipeline(gui))

        when:
        def yaml = builder.toYaml(config)

        then:
        yaml.startsWith('pipeline:\n  type: full\n  entryPoint: devSecOpsPipeline\n')
        yaml.contains('  agentNames:\n  - linux-agent\n')
        !yaml.contains('{')
        new Yaml().load(yaml) == config
    }
}
