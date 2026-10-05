package com.bbh.itss.dso.portal.dsoconfig

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.catalog.SonarSettings
import com.bbh.itss.dso.portal.catalog.SshTarget
import com.bbh.itss.dso.portal.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.catalog.Region
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException
import com.bbh.itss.dso.portal.pipeline.PipelineType
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DsoConfigBuilderSpec extends Specification {

    def settings = Stub(GlobalSettingsRepositoryPort) {
        load() >> Optional.of(storedSettings('https://jenkins.test'))
    }

    @Subject
    def builder = new DsoConfigBuilder(settings)

    def certScanner = product()
    def gui = service(certScanner, name: 'gui',
            sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonarqube'])),
            nexusIq: NexusIqSettings.of('cert-gui', ['**/build/libs/*.jar']))
    def api = service(certScanner, name: 'backend-api', build: build(tool: BuildTool.MAVEN),
            deployment: deployment(target: DeployTarget.OPENSHIFT, appName: 'cert-api', artifactName: 'cert-api.jar'))

    def "a product's config lists every service under projects in the template's key order"() {
        when:
        Map projects = builder.productConfig(certScanner).projects

        then:
        projects.keySet() as List == ['gui', 'backend-api']
        projects.gui.keySet() as List == ['appId', 'buildTool', 'deployTarget', 'sourceDir', 'javaPath', 'asoc', 'influx',
                                          'tools', 'dast', 'build', 'goldenFix']
        projects['backend-api'].appName == 'cert-api'
        projects['backend-api'].buildTool == 'maven'
        projects['backend-api'].build == [maven: [goals: ['clean', 'verify']]]
    }

    def "every service names the BBH tool servers of the global settings next to its own values"() {
        when:
        Map entry = builder.productConfig(certScanner).projects.gui

        then:
        entry.asoc == [url: 'https://bbh.cloud.appscan.com', keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        entry.influx == [url: 'http://qcwsecopsmon1.testbbh.com:8086/api/v2/write?org=DevSecOps&bucket=DORA-metrics&precision=s',
                         credentialsId: 'influxdb-token', enabled: true, project: 'CERT-gui', env: 'test']
        entry.tools.sonar == [serverUrl: 'https://tools.bbh.com/sonar', installationName: 'SonarQube',
                              projectName: 'CertScanner GUI', projectKey: 'cert-gui', gradle: [tasks: ['sonarqube']]]
        entry.tools.nexusIq == [serverUrl: 'https://tools.bbh.com/IQ', credentialsId: 'nexusiqP', application: 'cert-gui',
                                scanPatterns: ['**/build/libs/*.jar'], stage: 'build', failOnNetworkError: false]
    }

    def "the global deployment defaults fill in what a VM deployment leaves out"() {
        given:
        def batch = service(certScanner, name: 'batch',
                sshTargets: [(Region.RD): new SshTarget(null, 'batchadm', '/opt/batch', null, null)],
                urbanCodeApplications: [new UrbanCodeApplicationSettings('Batch', 1, ['RD'], null,
                        [new UrbanCodeComponent('batch-app', 'build/libs', '*.jar', null, null, null, false)])])

        when:
        Map deploy = builder.productConfig(certScanner).projects.batch.deploy

        then:
        deploy.vm.rd == [user: 'batchadm', deployDir: '/opt/batch', host: 'rdltaapps1.testbbh.com',
                         deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                         versionFile: 'scripts/deployment/version.properties']
        !deploy.vm.containsKey('qc')
        deploy.vm.dod.siteName == 'deploy.bbh.com'
        deploy.vm.dod.deployProcess == 'tomcat-app-process'
    }

    def "a pipeline receives its Jenkinsfile settings, the platform, the defaults and only its own service"() {
        when:
        def config = builder.pipelineConfig(pipeline(gui, agentLabels: ['linux-agent', 'docker']))

        then:
        config.keySet() as List == ['pipeline', 'platform', 'defaults', 'projects']
        config.pipeline == [type: 'full', entryPoint: 'devSecOpsPipeline', product: 'CERT', projectNames: 'gui',
                            agentNames: ['linux-agent', 'docker']]
        config.platform.jenkinsUrl == 'https://jenkins.test'
        config.platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
        config.platform.environment.APPSCAN_HOST == 'bbh.cloud.appscan.com'
        config.platform.environment.PROXY_PORT == '9090'
        config.defaults.coverage == [minLine: 60]
        config.defaults.releaseGate == [scanners: ['sast', 'sca', 'niq', 'dast'], requireCoverage: true,
                                        stateFile: 'release-gate.json']
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

    def "the extended pipeline names the security pipeline job whose results it reads"() {
        when:
        def config = builder.pipelineConfig(pipeline(gui, type: PipelineType.EXTENDED, securityPipelineJob: 'CERT/gui-security'))

        then:
        config.pipeline.securityPipeline == 'CERT/gui-security'
        !config.projects.gui.containsKey('jenkins')
    }

    def "a security pipeline without an extended job writes no jenkins section"() {
        expect:
        !builder.pipelineConfig(pipeline(gui, type: PipelineType.SECURITY)).projects.gui.containsKey('jenkins')
    }

    def "the global configuration holds the platform and the library defaults"() {
        when:
        def config = builder.globalConfig()

        then:
        config.keySet() as List == ['platform', 'defaults']
        config.defaults.keySet() as List == ['buildTool', 'deployTarget', 'sourceDir', 'coverage', 'tools', 'sast', 'sca',
                                             'dast', 'tests', 'releaseGate', 'goldenFix']
        config.defaults.tools.nexusIq == [maxCritical: 0, maxHigh: 0, maxMedium: 0]
    }

    def "rendering without stored global settings is an error of the start-up"() {
        given:
        def empty = new DsoConfigBuilder(Stub(GlobalSettingsRepositoryPort) { load() >> Optional.empty() })

        when:
        empty.globalConfig()

        then:
        def e = thrown(MissingGlobalSettingsException)
        e.message == 'The global settings are missing; the portal creates them at start-up'
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
