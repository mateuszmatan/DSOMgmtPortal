package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.adapter.RecordMapper
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
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

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.POWERSHELL_PATH_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.SHELL_SAFE_MESSAGE
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE
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
        dto.appScan().compileCommand() == new ServiceDto.ToolCommandDto([], [], null, null, [], null, false)
        dto.scm().reviewers() == ['alice']
        dto.sshTargets()[QC] == new ServiceDto.SshTargetDto('qc.host', null, null, null, null)
        dto.urbanCodeApplications()[0].applicationName() == 'Cert'
        dto.urbanCodeApplications()[0].environments() == ['RD']
        dto.urbanCodeApplications()[0].components() == []
        dto.testJobs()[0].job() == 'CERT/smoke'
        dto.testJobs()[0].parameters() == 'A=1'
        dto.metrics() == null
    }

    def "bean validation rejects #description and says what it expects"() {
        expect:
        validator.validate(request(toJson(serviceJson(changes)))).collect { [it.propertyPath.toString(), it.message] } ==
                [[property, message]]

        where:
        description                   | changes                                                       || property                                         | message
        'a service name with a space' | [name: 'Gui app']                                             || 'name'                                           | "use letters, digits, '.', '-' or '_', starting with a letter or digit"
        'a missing build tool'        | [build: [javaPath: '/jdk']]                                   || 'build.tool'                                     | 'must not be null'
        'more than 30 build tasks'    | [build: buildJson(command: [tasks: names(31)])]               || 'build.command.tasks'                            | 'size must be between 0 and 30'
        'a variable without a value'  | [build: buildJson(command: [environment: ['JAVA']])]          || 'build.command.environment[0].<list element>'    | 'write each variable as NAME=value'
        'a test job without time'     | [testJobs: [[stage: 'SMOKE', job: 'j', timeoutMinutes: 0]]]   || 'testJobs[0].timeoutMinutes'                    | 'must be greater than or equal to 1'
        'an SSH host with a space'    | [sshTargets: [RD: [host: 'rd host']]]                         || 'sshTargets[RD].host'                            | SHELL_SAFE_MESSAGE
        'a build path with a command' | [build: buildJson(buildPath: 'target/*.jar;rm')]              || 'build.buildPath'                                | SHELL_SAFE_MESSAGE
        'a Dockerfile with a space'   | [openShiftTargets: [RD: [dockerFilePath: 'my Dockerfile']]]   || 'openShiftTargets[RD].dockerFilePath'            | SHELL_SAFE_MESSAGE
        'a component without a name'  | [urbanCodeApplications: [[applicationName: 'C', components: [[:]]]]] || 'urbanCodeApplications[0].components[0].componentName' | 'must not be blank'
        'a DAST URL without http'     | [appScan: [applicationId: APP_ID, dastTargetUrl: 'ftp://x']]  || 'appScan.dastTargetUrl'                          | URL_MESSAGE
        'a workspace with a space'    | [scm: [workspace: 'ta workspace']]                            || 'scm.workspace'                                  | 'must not contain whitespace'
        'an author email without @'   | [goldenFix: [commitAuthorEmail: 'goldenfix.bbh.com']]         || 'goldenFix.commitAuthorEmail'                    | 'must be a well-formed email address'
        'a build tag with a space'    | [openShiftTargets: [QC: [buildTag: '1.0 rc']]]                || 'openShiftTargets[QC].buildTag'                  | SHELL_SAFE_MESSAGE
        'an InfluxDB URL without http'| [metrics: [influxUrl: 'influx:8086']]                         || 'metrics.influxUrl'                              | URL_MESSAGE
        'a URL with a command'        | [scm: [repositoryUrl: 'https://bitbucket/$(id)']]             || 'scm.repositoryUrl'                              | URL_MESSAGE
        'a URL with a backtick'       | [metrics: [influxUrl: 'http://influx/`id`']]                  || 'metrics.influxUrl'                              | URL_MESSAGE
        'a URL with a double quote'   | [testJobs: [[stage: 'SMOKE', job: 'j', remoteJenkinsUrl: 'https://j/"x']]] || 'testJobs[0].remoteJenkinsUrl'     | URL_MESSAGE
        'a URL with a backslash'      | [appScan: [applicationId: APP_ID, dastTargetUrl: 'https://cert\\x']] || 'appScan.dastTargetUrl'                | URL_MESSAGE
        'a Nexus IQ app without name' | [nexusIqApplications: [[scanPatterns: ['**/*.jar']]]]         || 'nexusIqApplications[0].application'            | 'must not be blank'
        'a poll interval of zero'     | [tests: [smokePollIntervalSec: 0]]                            || 'tests.smokePollIntervalSec'                     | 'must be greater than or equal to 1'
        'a Maven home with a command' | [build: buildJson(command: [mavenHome: '/opt/maven;id'])]     || 'build.command.mavenHome'                        | SHELL_SAFE_MESSAGE
        'a Flutter plugin with $()'   | [flutter: [deliveryPlugin: 'deploy:deploy-file$(id)']]        || 'flutter.deliveryPlugin'                         | SHELL_SAFE_MESSAGE
        'an AppScan client with a ;'  | [appScan: [applicationId: APP_ID, clientPath: 'bin/appscan.bat;id']] || 'appScan.clientPath'                    | POWERSHELL_PATH_MESSAGE
        'a folder with a quote'       | [appScan: [applicationId: APP_ID, includedDirs: ["it's"]]]    || 'appScan.includedDirs[0].<list element>'        | 'one folder per entry, without commas or quotes'
    }

    def "an AppScan client path may use Windows separators"() {
        expect:
        validator.validate(request(toJson(serviceJson(appScan: [applicationId: APP_ID,
                clientPath: '\\SAClientUtil\\bin\\appscan.bat'])))).isEmpty()
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
                new TestSettings(5, 1, 2, 3, true, true, true, null, null, null),
                [TestJob.of(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', 10)],
                deployment(appName: 'gui'),
                command(['deploy:deploy-file']),
                new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'desc', 'a=b'),
                [UrbanCodeApplicationSettings.of('Cert', 1, ['RD'], 'snap',
                        [new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', null, null, null, false, null, null, null, null, null)])],
                [(QC): new SshTarget('qc.host', null, null, null, null), (RD): new SshTarget('rd.host', null, null, null, null)],
                [(QC): new OpenShiftTarget(null, null, null, null, null, null, 'pull/cert', null, null, 'cert-qc', null, null,
                        true, null, null, null, null, null, null, null, null)],
                appScan(dastEnabled: true, dastTargetUrl: 'https://rdl1.testbbh.com', compileCommand: command(['compile'])),
                SonarSettings.of('Cert', 'cert-gui', command(['sonar:sonar'])),
                new NexusIqSettings('https://iq.bbh.com', 'iq-creds', null),
                [NexusIqApplication.of('cert', ['**/*.war']), NexusIqApplication.of('cert-batch', ['**/batch/*.jar'])],
                new ScmSettings('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds', null, null, null, null, null,
                        'https://bitbucket.bbh.com/rest/api/1.0', 'ta-workspace', 'TA', 'cert-gui'),
                GoldenFixPolicy.inherit(false),
                new MetricsSettings(false, 'cert-gui', 'qc', null, null),
                new FlutterSettings(FlutterPlatform.WEB, ['app'], [], [], [], 's', 'p', 't', null, null, null, null, null, true,
                        null, null))
    }
}
