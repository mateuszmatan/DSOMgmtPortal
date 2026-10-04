package com.bbh.dso.portal.catalog

import com.bbh.dso.portal.common.ValidationProblems
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.dso.portal.catalog.BuildTool.FLUTTER
import static com.bbh.dso.portal.catalog.BuildTool.GRADLE
import static com.bbh.dso.portal.catalog.BuildTool.MAVEN
import static com.bbh.dso.portal.catalog.DeployTarget.OPENSHIFT
import static com.bbh.dso.portal.catalog.DeployTarget.VM

class ConfigSectionsSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "build settings default the source directory and write the build keys"() {
        when:
        def build = new BuildSettings(MAVEN, ' ', ' /opt/jdk-17 ', null)

        then:
        build.sourceDir() == '.'
        build.javaPath() == '/opt/jdk-17'
        !build.autoSetup()
        written(build) == [buildTool: 'maven', sourceDir: '.', javaPath: '/opt/jdk-17']
    }

    def "automatic build tool setup is written only when it is enabled"() {
        expect:
        written(new BuildSettings(GRADLE, 'app', null, true)) == [buildTool: 'gradle', sourceDir: 'app', buildToolAutoSetup: true]
    }

    def "a #tool service with java path #javaPath and auto setup #autoSetup reports #fields"() {
        expect:
        problems(new BuildSettings(tool, null, javaPath, autoSetup)) == fields

        where:
        tool    | javaPath | autoSetup || fields
        GRADLE  | null     | false     || ['javaPath']
        MAVEN   | null     | null      || ['javaPath']
        GRADLE  | '/jdk'   | false     || []
        MAVEN   | null     | true      || []
        FLUTTER | null     | false     || []
    }

    def "OpenShift deployment needs the application and artifact names"() {
        expect:
        problems(new DeploymentSettings(OPENSHIFT, ' ', null)) == ['appName', 'artifactName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', 'gui.jar')) == []
        problems(new DeploymentSettings(VM, null, null)) == []
        written(new DeploymentSettings(OPENSHIFT, ' gui ', 'gui.jar')) == [deployTarget: 'openshift', appName: 'gui', artifactName: 'gui.jar']
        written(new DeploymentSettings(VM, null, null)) == [deployTarget: 'vm']
    }

    def "AppScan settings store the application ID in lower case with DAST off by default"() {
        when:
        def appScan = new AppScanSettings(' 109F44AC-CC06-4CA0-884E-D944904F7019 ', ' ', null, ' ', null)

        then:
        appScan.applicationId() == APP_ID
        appScan.sastScanName() == null
        !appScan.dastEnabled()
        appScan.dastTargetUrl() == null
        written(appScan) == [appId: APP_ID, dast: [enabled: false]]
        new AppScanSettings(null, null, null, null, null).applicationId() == null
    }

    def "enabled DAST writes its target and needs a URL"() {
        expect:
        written(new AppScanSettings(APP_ID, 'cert-gui', true, 'https://rdl1.testbbh.com', 'p-1')) ==
                [appId: APP_ID, sast: [scanName: 'cert-gui'],
                 dast : [enabled: true, targetUrl: 'https://rdl1.testbbh.com', presenceId: 'p-1']]
        problems(new AppScanSettings(APP_ID, null, true, null, null)) == ['dastTargetUrl']
        problems(new AppScanSettings(APP_ID, null, false, null, null)) == []
    }

    def "SonarQube settings write the tools.sonar keys"() {
        expect:
        written(new SonarSettings(' CertScanner ', ' cert-scanner ')) == [tools: [sonar: [projectName: 'CertScanner', projectKey: 'cert-scanner']]]
        written(SonarSettings.NONE) == [:]
    }

    def "Nexus IQ settings keep each scan pattern once"() {
        when:
        def nexusIq = new NexusIqSettings(' cert ', ['**/*.jar', ' ', '**/*.jar', ' **/*.war '])

        then:
        nexusIq.scanPatterns() == ['**/*.jar', '**/*.war']
        written(nexusIq) == [tools: [nexusIq: [application: 'cert', scanPatterns: ['**/*.jar', '**/*.war']]]]
        written(NexusIqSettings.NONE) == [:]
        new NexusIqSettings(null, null).scanPatterns() == []
    }

    def "GoldenFix stays on unless it is switched off"() {
        expect:
        new ScmSettings(null, null, null).goldenFixEnabled()
        !new ScmSettings(null, null, false).goldenFixEnabled()
        written(ScmSettings.NONE) == [goldenFix: [enabled: true]]
        written(new ScmSettings(' https://bitbucket.bbh.com/r ', 'bb-creds', false)) ==
                [scm: [bitbucket: [url: 'https://bitbucket.bbh.com/r', credentialsId: 'bb-creds']], goldenFix: [enabled: false]]
    }

    def "metrics are on by default and use the test environment"() {
        expect:
        MetricsSettings.DEFAULTS.enabled()
        MetricsSettings.DEFAULTS.influxProject() == null
        MetricsSettings.DEFAULTS.influxEnv() == 'test'
        new MetricsSettings(false, ' cert ', ' prod ') == new MetricsSettings(false, 'cert', 'prod')
        written(new MetricsSettings(null, 'CERT-gui', null)) == [influx: [enabled: true, project: 'CERT-gui', env: 'test']]
    }

    def "a missing metrics project defaults to the product code and service name"() {
        given:
        def explicit = new MetricsSettings(true, 'cert-scanner', 'uat')

        expect:
        MetricsSettings.DEFAULTS.withDefaultProject('CERT', 'gui') == new MetricsSettings(true, 'CERT-gui', 'test')
        explicit.withDefaultProject('CERT', 'gui').is(explicit)
    }

    def "the AppScan account writes the key ID and the credential holding the secret"() {
        expect:
        written(new AppScanAccount(' bbh_key ', ' asoc-creds ')) == [asoc: [keyId: 'bbh_key', token: 'asoc-creds']]
        written(new AppScanAccount('bbh_key', ' ')) == [asoc: [keyId: 'bbh_key']]
    }

    def "build tools and deploy targets are written in lower case"() {
        expect:
        BuildTool.values()*.configValue() == ['gradle', 'maven', 'flutter']
        DeployTarget.values()*.configValue() == ['vm', 'openshift']
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                     | section                                                       || property
        'an AppScan ID that is no UUID' | new AppScanSettings('not-a-uuid', null, false, null, null)    || 'applicationId'
        'a DAST URL without http'       | new AppScanSettings(APP_ID, null, true, 'ftp://x', null)      || 'dastTargetUrl'
        'a SonarQube key of digits'     | new SonarSettings(null, '1234')                               || 'projectKey'
        'a metrics tag with a space'    | new MetricsSettings(true, 'cert scanner', null)               || 'influxProject'
        'a repository that is no URL'   | new ScmSettings('bitbucket', null, true)                      || 'repositoryUrl'
        'more than 20 scan patterns'    | new NexusIqSettings(null, (1..21).collect { "p$it" as String }) || 'scanPatterns'
        'a missing build tool'          | new BuildSettings(null, null, '/jdk', false)                  || 'tool'
        'a missing AppScan key ID'      | new AppScanAccount(' ', null)                                 || 'keyId'
    }

    private static Map written(ConfigSection section) {
        def tree = new ConfigTree()
        section.writeTo(tree)
        tree.toMap()
    }

    private static List<String> problems(ConfigSection section) {
        def problems = new ValidationProblems()
        section.validate(problems)
        problems.list()*.field
    }
}
