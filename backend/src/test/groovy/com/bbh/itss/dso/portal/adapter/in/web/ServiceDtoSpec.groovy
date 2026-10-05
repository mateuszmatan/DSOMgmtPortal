package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.TestJob
import com.bbh.itss.dso.portal.domain.catalog.TestSettings
import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.ApiJson.APP_ID
import static com.bbh.itss.dso.portal.support.ApiJson.build as buildJson
import static com.bbh.itss.dso.portal.support.ApiJson.service as serviceJson
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment

class ServiceDtoSpec extends Specification {

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    @Shared
    JsonMapper json = JsonMapper.builder().build()

    def "a service with every section travels through the API and back unchanged"() {
        given:
        def settings = fullSettings()

        when:
        def dto = ServiceDto.from(new Service(7L, 'gui', ' Angular front end ', 0, settings))

        then:
        dto.description() == 'Angular front end'
        dto.sshTargets().keySet() as List == [RD, QC]
        dto.scm().repoSlug() == 'cert-gui'
        request(json.writeValueAsString(dto)) == dto
        validator.validate(dto).isEmpty()
        with(dto.toDraft()) {
            id() == 7L
            name() == 'gui'
            it.settings() == settings
        }
    }

    def "a request with only the required sections gets the defaults of the others"() {
        when:
        def settings = request(toJson(serviceJson(sshTargets: [RD: null, QC: [host: 'qc.host']]))).toDraft().settings()

        then:
        settings.unitTests() == UnitTestSettings.NONE
        settings.tests() == TestSettings.DEFAULTS
        settings.goldenFix() == GoldenFixPolicy.INHERITED
        settings.metrics() == MetricsSettings.DEFAULTS
        settings.sshTargets() == [(QC): new SshTarget('qc.host', null, null, null, null)]
        settings.openShiftTargets() == [:]
    }

    def "a request is normalised the way the domain normalises before it is validated"() {
        when:
        def dto = request(toJson(serviceJson(
                appScan: [applicationId: " ${APP_ID.toUpperCase()} ", includedDirs: [' src ', '', 'src']],
                scm: [reviewers: [' alice ', 'alice']],
                sshTargets: [QC: [host: ' qc.host ', user: ' ']],
                urbanCodeApplications: [[applicationName: ' Cert ', environments: [' RD ', 'RD']]],
                testJobs: [[stage: 'SMOKE', job: ' CERT/smoke ', parameters: ' A=1 ']])))

        then:
        validator.validate(dto).isEmpty()
        dto.appScan().applicationId() == APP_ID
        dto.appScan().includedDirs() == ['src']
        dto.appScan().compile()
        dto.appScan().compileCommand() == new ServiceDto.ToolCommandDto([], [], null, null, [])
        dto.scm().reviewers() == ['alice']
        dto.sshTargets()[QC] == new ServiceDto.SshTargetDto('qc.host', null, null, null, null)
        dto.urbanCodeApplications()[0].applicationName() == 'Cert'
        dto.urbanCodeApplications()[0].environments() == ['RD']
        dto.urbanCodeApplications()[0].components() == []
        dto.testJobs()[0].job() == 'CERT/smoke'
        dto.testJobs()[0].parameters() == 'A=1'
        dto.metrics() == null
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(request(toJson(serviceJson(changes))))*.propertyPath*.toString() == [property]

        where:
        description                       | changes                                                              || property
        'a service name in capitals'      | [name: 'Gui']                                                        || 'name'
        'a missing build tool'            | [build: [javaPath: '/jdk']]                                          || 'build.tool'
        'more than 30 build tasks'        | [build: buildJson(command: [tasks: names(31)])]                      || 'build.command.tasks'
        'a variable without a value'      | [build: buildJson(command: [environment: ['JAVA']])]                 || 'build.command.environment[0].<list element>'
        'too many parallel smoke jobs'    | [tests: [smokeMaxParallel: 101]]                                     || 'tests.smokeMaxParallel'
        'a test job without a stage'      | [testJobs: [[job: 'CERT/s']]]                                        || 'testJobs[0].stage'
        'a blank test job'                | [testJobs: [[stage: 'SMOKE', job: ' ']]]                             || 'testJobs[0].job'
        'a test job running over a day'   | [testJobs: [[stage: 'SMOKE', job: 'CERT/s', timeoutMinutes: 1441]]]  || 'testJobs[0].timeoutMinutes'
        'a remote Jenkins that is no URL' | [testJobs: [[stage: 'SMOKE', job: 'j', remoteJenkinsUrl: 'jenkins']]] || 'testJobs[0].remoteJenkinsUrl'
        'a missing deploy target'         | [deployment: [appName: 'gui']]                                       || 'deployment.target'
        'an SSH host with a space'        | [sshTargets: [RD: [host: 'rd host']]]                                || 'sshTargets[RD].host'
        'a deployment repository w/o URL' | [openShiftTargets: [QC: [deploymentRepoUrl: 'bitbucket/ta']]]        || 'openShiftTargets[QC].deploymentRepoUrl'
        'an UrbanCode site too long'      | [urbanCode: [siteName: 's' * 201]]                                   || 'urbanCode.siteName'
        'an application ordered 0'        | [urbanCodeApplications: [[applicationName: 'Cert', order: 0]]]       || 'urbanCodeApplications[0].order'
        'a component without a name'      | [urbanCodeApplications: [[applicationName: 'C', components: [[:]]]]] || 'urbanCodeApplications[0].components[0].componentName'
        'an AppScan ID that is no UUID'   | [appScan: [applicationId: 'not-a-uuid']]                             || 'appScan.applicationId'
        'a folder with a comma'           | [appScan: [applicationId: APP_ID, excludedDirs: ['src,lib']]]        || 'appScan.excludedDirs[0].<list element>'
        'a DAST URL without http'         | [appScan: [applicationId: APP_ID, dastTargetUrl: 'ftp://rdl1']]      || 'appScan.dastTargetUrl'
        'a SonarQube key of digits'       | [sonar: [projectKey: '1234']]                                        || 'sonar.projectKey'
        'a Nexus IQ stage in upper case'  | [nexusIq: [stage: 'Release']]                                        || 'nexusIq.stage'
        'a clone URL in scp form'         | [scm: [cloneUrl: 'git@bitbucket:ta/cert.git']]                       || 'scm.cloneUrl'
        'a reviewer with a space'         | [scm: [reviewers: ['john doe']]]                                     || 'scm.reviewers[0].<list element>'
        'a workspace with a space'        | [scm: [workspace: 'ta workspace']]                                   || 'scm.workspace'
        'an unknown ecosystem'            | [goldenFix: [ecosystems: ['gradle']]]                                || 'goldenFix.ecosystems[0].<list element>'
        'an author email without @'       | [goldenFix: [commitAuthorEmail: 'goldenfix.bbh.com']]                || 'goldenFix.commitAuthorEmail'
        'a time zone with a space'        | [goldenFix: [timeZone: 'Europe Warsaw']]                             || 'goldenFix.timeZone'
        'a metrics tag with a space'      | [metrics: [influxProject: 'cert scanner']]                           || 'metrics.influxProject'
        'a Flutter module with a space'   | [flutter: [modules: ['my module']]]                                  || 'flutter.modules[0].<list element>'
        'a SonarScanner version with ws'  | [flutter: [sonarScannerVersion: '5.0 beta']]                         || 'flutter.sonarScannerVersion'
    }

    def "the format rules explain what they expect"() {
        expect:
        validator.validate(request(toJson(serviceJson(changes))))*.message == [message]

        where:
        changes                                                     || message
        [build: buildJson(command: [environment: ['JAVA']])]        || 'write each variable as NAME=value'
        [sshTargets: [RD: [host: 'rd host']]]                       || 'must be a host name such as rdltaapps1.testbbh.com'
        [openShiftTargets: [QC: [deploymentRepoUrl: 'bitbucket']]]  || 'must be a Git repository URL'
        [appScan: [applicationId: APP_ID, dastTargetUrl: 'ftp://x']] || 'must be an http or https URL'
        [scm: [cloneUrl: 'git@bitbucket:ta/cert.git']]              || 'must be an http, https or ssh URL'
        [scm: [workspace: 'ta workspace']]                          || 'must not contain whitespace'
        [flutter: [testSubplugins: ['plugin*']]]                    || 'must be a plugin folder name'
    }

    private ServiceDto request(String body) {
        RecordMapper.map(json.readValue(body, ServiceDto), ServiceDto)
    }

    private static List<String> names(int count) {
        (1..count).collect { "t$it" as String }
    }

    static ServiceSettings fullSettings() {
        new ServiceSettings(build(tool: BuildTool.MAVEN, buildPath: 'target/gui.war'),
                new UnitTestSettings(command(['test']), '**/TEST-*.xml', null, null, true, null),
                new TestSettings(5, 1, 2, 3),
                [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', 10, null, null, null, null)],
                deployment(appName: 'gui'),
                command(['deploy:deploy-file']),
                new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'desc', 'a=b'),
                [new UrbanCodeApplicationSettings('Cert', 1, ['RD'], 'snap',
                        [new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', null, null, null, false)])],
                [(QC): new SshTarget('qc.host', null, null, null, null), (RD): new SshTarget('rd.host', null, null, null, null)],
                [(QC): new OpenShiftTarget(null, null, null, null, null, null, 'pull/cert', null, null, 'cert-qc', null, null,
                        true, null, null, null, null, null, null)],
                appScan(dastEnabled: true, dastTargetUrl: 'https://rdl1.testbbh.com', compileCommand: command(['compile'])),
                SonarSettings.of('Cert', 'cert-gui', command(['sonar:sonar'])),
                NexusIqSettings.of('cert', ['**/*.war']),
                new ScmSettings('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds', null, null, null, null, null,
                        'https://bitbucket.bbh.com/rest/api/1.0', 'ta-workspace', 'TA', 'cert-gui'),
                GoldenFixPolicy.inherit(false),
                new MetricsSettings(false, 'cert-gui', 'qc'),
                new FlutterSettings(FlutterPlatform.WEB, ['app'], [], [], [], 's', 'p', 't', null, null, null, null, null, true,
                        null, null))
    }
}
