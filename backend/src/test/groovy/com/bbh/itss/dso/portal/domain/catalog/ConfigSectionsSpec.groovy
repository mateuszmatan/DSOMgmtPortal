package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketType.CLOUD
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketType.SERVER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM
import static com.bbh.itss.dso.portal.domain.shared.Sections.messages
import static com.bbh.itss.dso.portal.domain.shared.Sections.problems
import static com.bbh.itss.dso.portal.domain.shared.Sections.reported
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class ConfigSectionsSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'

    def "build settings default the source directory, trim the paths and start without a command"() {
        when:
        def build = new BuildSettings(MAVEN, ' ', ' /opt/jdk-17 ', null, ' target/cert.jar ', null)

        then:
        build == new BuildSettings(MAVEN, '.', '/opt/jdk-17', false, 'target/cert.jar', ToolCommand.NONE)
        written(build) == [buildTool: 'maven', sourceDir: '.', javaPath: '/opt/jdk-17', build: [buildPath: 'target/cert.jar']]
        new BuildSettings(GRADLE, null, ' ', false, ' ', null).javaPath() == null
        new BuildSettings(GRADLE, null, null, false, ' ', null).buildPath() == null
    }

    def "a Maven build writes its goals, flags, directory, Maven installation and environment under build.maven"() {
        given:
        def command = new ToolCommand(['clean', 'verify'], ['-B', '-s', 'settings.xml'], ' gui ', ' /opt/maven ',
                ['MAVEN_OPTS=-Xmx1g'])

        expect:
        written(new BuildSettings(MAVEN, 'gui', '/jdk', false, null, command)) ==
                [buildTool: 'maven', sourceDir: 'gui', javaPath: '/jdk',
                 build    : [maven: [goals : ['clean', 'verify'], flags: ['-B', '-s', 'settings.xml'], dir: 'gui',
                                     mvnPath: '/opt/maven', env: [MAVEN_OPTS: '-Xmx1g']]]]
    }

    def "automatic build tool setup is written only when it is enabled, and a Flutter build writes no command"() {
        expect:
        written(new BuildSettings(GRADLE, 'app', null, true, null, ToolCommand.of(['build'], []))) ==
                [buildTool: 'gradle', sourceDir: 'app', buildToolAutoSetup: true, build: [gradle: [tasks: ['build']]]]
        written(new BuildSettings(FLUTTER, null, null, false, 'build/app.apk', ToolCommand.of(['assemble'], ['-q']))) ==
                [buildTool: 'flutter', sourceDir: '.', build: [buildPath: 'build/app.apk']]
    }

    def "a #tool build with java path #javaPath, auto setup #autoSetup and tasks #tasks reports #fields"() {
        expect:
        problems(new BuildSettings(tool, null, javaPath, autoSetup, null, ToolCommand.of(tasks, []))) == fields

        where:
        tool    | javaPath | autoSetup | tasks     || fields
        GRADLE  | null     | false     | ['build'] || ['javaPath']
        MAVEN   | null     | null      | ['verify']|| ['javaPath']
        GRADLE  | '/jdk'   | false     | []        || ['command.tasks']
        MAVEN   | null     | true      | []        || ['command.tasks']
        GRADLE  | null     | false     | []        || ['javaPath', 'command.tasks']
        GRADLE  | '/jdk'   | false     | ['build'] || []
        MAVEN   | null     | true      | ['verify']|| []
        FLUTTER | null     | false     | []        || ['javaPath']
        FLUTTER | '/jdk'   | true      | []        || ['autoSetup']
        FLUTTER | null     | true      | []        || ['javaPath', 'autoSetup']
        FLUTTER | '/jdk'   | false     | []        || []
    }

    def "OpenShift deployment needs the application and artifact names"() {
        expect:
        problems(new DeploymentSettings(OPENSHIFT, ' ', null, 'gui-1.0.jar')) == ['appName', 'artifactName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', null, null)) == ['artifactName']
        problems(new DeploymentSettings(OPENSHIFT, null, 'gui.jar', null)) == ['appName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', 'gui.jar', null)) == []
        problems(new DeploymentSettings(VM, null, null, null)) == []
        messages(new DeploymentSettings(OPENSHIFT, null, null, null)) ==
                ['is required for OpenShift deployment', 'is required for OpenShift deployment']
    }

    def "deployment settings write the target, the names and the base artifact name"() {
        when:
        def deployment = new DeploymentSettings(OPENSHIFT, ' gui ', ' gui.jar ', ' gui-1.0.0.jar ')

        then:
        written(deployment) == [deployTarget    : 'openshift', appName: 'gui', artifactName: 'gui.jar',
                                baseArtifactName: 'gui-1.0.0.jar']
        written(new DeploymentSettings(VM, ' ', '', ' ')) == [deployTarget: 'vm']
        new DeploymentSettings(VM, ' ', '', ' ') == new DeploymentSettings(VM, null, null, null)
    }

    def "AppScan settings store the application ID in lower case and start at the library's defaults"() {
        when:
        def appScan = new AppScanSettings(' 109F44AC-CC06-4CA0-884E-D944904F7019 ', ' ', [' src ', '', 'src', null],
                null, null, null, null, null, ' ', null, null, ' ', ' ', ' ')

        then:
        appScan == new AppScanSettings(APP_ID, null, ['src'], [], true, false, false, false, null, ToolCommand.NONE,
                false, null, null, null)
        written { appScan.writeTo(it, GRADLE) } == [appId: APP_ID, includedDirs: 'src', dast: [enabled: false]]
        written { AppScanSettings.of(APP_ID).writeTo(it, MAVEN) } == [appId: APP_ID, dast: [enabled: false]]
        AppScanSettings.of(null).applicationId() == null
    }

    def "every AppScan option that differs from the library's default is written"() {
        given:
        def appScan = new AppScanSettings(APP_ID, ' cert-gui ', ['src/main', ' lib '], ['src/test'], false, true, true,
                true, ' /opt/appscan ', new ToolCommand(['compileJava'], ['--offline'], 'gui', '/opt/maven',
                ['JAVA_OPTS=-Xmx2g']), true, ' cert-gui-dast ', ' https://rdl1.testbbh.com ', ' p-1 ')

        expect:
        written { appScan.writeTo(it, GRADLE) } ==
                [appId      : APP_ID, includedDirs: 'src/main,lib', excludedDirs: 'src/test', appscanPath: '/opt/appscan',
                 asoc       : [doCompile: false, sourceCodeOnly: true, useAppScanConfig: true, insecureTls: true,
                               gradle   : [tasks: ['compileJava'], flags: ['--offline'], dir: 'gui',
                                           env  : [JAVA_OPTS: '-Xmx2g']]],
                 sast       : [scanName: 'cert-gui'],
                 dast       : [enabled: true, scanName: 'cert-gui-dast', targetUrl: 'https://rdl1.testbbh.com',
                               presenceId: 'p-1']]
    }

    def "the AppScan compile command goes under asoc.#key for #tool"() {
        given:
        def appScan = new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null,
                new ToolCommand(['compile'], [], null, '/opt/maven', []), false, null, null, null)

        expect:
        written { appScan.writeTo(it, tool) }.asoc == asoc

        where:
        tool    || key      | asoc
        GRADLE  || 'gradle' | [gradle: [tasks: ['compile']]]
        MAVEN   || 'maven'  | [maven: [goals: ['compile'], mvnPath: '/opt/maven']]
        FLUTTER || 'none'   | null
    }

    def "enabled DAST needs a target URL"() {
        expect:
        reported { dast(null).validate(it) }.collect { [it.field, it.message] } ==
                [['dastTargetUrl', 'is required when DAST is enabled']]
        problems { dast('https://x').validate(it) } == []
        problems { AppScanSettings.of(APP_ID).validate(it) } == []
    }

    def "SonarQube settings trim their values and switch the badges off by default"() {
        when:
        def sonar = new SonarSettings(' ', ' cert ', ' ', ' ', ' ', ' ', null, null, null)

        then:
        sonar == new SonarSettings(null, 'cert', null, null, null, null, false, false, ToolCommand.NONE)
        SonarSettings.of(' CertScanner ', 'cert', null) == new SonarSettings('CertScanner', 'cert', null, null, null, null,
                false, false, ToolCommand.NONE)
        SonarSettings.NONE == new SonarSettings(' ', '', null, null, null, null, false, false, null)
    }

    def "SonarQube settings write every tools.sonar key with the analysis command of the build tool"() {
        given:
        def sonar = new SonarSettings(' CertScanner ', ' cert-scanner ', ' SonarQube BBH ', ' sonar-creds ',
                ' sonar-token ', ' sqb_1a2b ', true, true, ToolCommand.of(['sonar:sonar'], ['-Dsonar.branch.name=main']))

        expect:
        written { sonar.writeTo(it, MAVEN) } ==
                [tools: [sonar: [projectName     : 'CertScanner', projectKey: 'cert-scanner',
                                 installationName: 'SonarQube BBH', credentialsId: 'sonar-creds',
                                 authToken       : 'sonar-token', badgeToken: 'sqb_1a2b', addBadges: true, fullBadges: true,
                                 maven           : [goals: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]]]]
        written { sonar.writeTo(it, GRADLE) }.tools.sonar.gradle == [tasks: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]
        written { sonar.writeTo(it, FLUTTER) }.tools.sonar.keySet() as List ==
                ['projectName', 'projectKey', 'installationName', 'credentialsId', 'authToken', 'badgeToken', 'addBadges',
                 'fullBadges']
        written { SonarSettings.of('CertScanner', 'cert', null).writeTo(it, GRADLE) } ==
                [tools: [sonar: [projectName: 'CertScanner', projectKey: 'cert']]]
        written { SonarSettings.NONE.writeTo(it, GRADLE) } == [:]
    }

    def "a #tool SonarQube project with key #key and tasks #tasks reports #fields"() {
        expect:
        problems { SonarSettings.of(null, key, ToolCommand.of(tasks, [])).validate(it, tool) } == fields

        where:
        tool    | key    | tasks           || fields
        GRADLE  | 'cert' | []              || ['command.tasks']
        MAVEN   | 'cert' | []              || ['command.tasks']
        GRADLE  | null   | []              || []
        FLUTTER | 'cert' | []              || []
        MAVEN   | 'cert' | ['sonar:sonar'] || []
        GRADLE  | 'cert' | ['sonarqube']   || []
    }

    def "a missing #tool command of the #stage is explained in the words of the build tool"() {
        expect:
        messages(validation) == [message]

        where:
        tool     | stage      | validation                                                                  || message
        'Maven'  | 'build'    | new BuildSettings(MAVEN, null, '/jdk', false, null, null)                   || 'add the Maven goals of the build, for example clean verify'
        'Gradle' | 'build'    | new BuildSettings(GRADLE, null, '/jdk', false, null, null)                  || 'add the Gradle tasks of the build, for example clean build'
        'JDK'    | 'build'    | new BuildSettings(GRADLE, null, null, false, null, ToolCommand.of(['build'], [])) || 'set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them'
        'Maven'  | 'analysis' | { SonarSettings.of(null, 'cert', null).validate(it, MAVEN) }                || 'add the Maven goals of the analysis, for example sonar:sonar'
        'Gradle' | 'analysis' | { SonarSettings.of(null, 'cert', null).validate(it, GRADLE) }               || 'add the Gradle tasks of the analysis, for example sonarqube'
    }

    def "Nexus IQ settings keep each scan pattern once and default the stage to build"() {
        when:
        def nexusIq = new NexusIqSettings(' cert ', ['**/*.jar', ' ', '**/*.jar', ' **/*.war '], ' ', null, ' ')

        then:
        nexusIq == new NexusIqSettings('cert', ['**/*.jar', '**/*.war'], 'build', false, null)
        written(nexusIq) == [tools: [nexusIq: [application: 'cert', scanPatterns: ['**/*.jar', '**/*.war'], stage: 'build',
                                               failOnNetworkError: false]]]
        new NexusIqSettings(null, null, null, null, null).scanPatterns() == []
        NexusIqSettings.of(' cert ', ['**/*.jar']) == new NexusIqSettings('cert', ['**/*.jar'], 'build', false, null)
    }

    def "Nexus IQ settings write the stage, the network error policy and the SCA scan name"() {
        expect:
        written(new NexusIqSettings('cert', ['**/*.jar'], ' stage-release ', true, ' cert-sca ')) ==
                [tools: [nexusIq: [application: 'cert', scanPatterns: ['**/*.jar'], stage: 'stage-release',
                                   failOnNetworkError: true]],
                 sca  : [scanName: 'cert-sca']]
        written(NexusIqSettings.NONE) == [tools: [nexusIq: [stage: 'build', failOnNetworkError: false]]]
        problems(NexusIqSettings.NONE) == []
    }

    def "SCM settings trim their values, sign in with a password by default and keep each reviewer once"() {
        when:
        def scm = new ScmSettings(' ', ' ', null, null, ' ', ' ', [' alice ', 'alice', '', 'bob'], ' ', ' ', ' ', ' ')

        then:
        scm == new ScmSettings(null, null, BASIC, null, null, null, ['alice', 'bob'], null, null, null, null)
        ScmSettings.NONE == new ScmSettings(null, null, null, null, null, null, null, null, null, null, null)
        ScmSettings.of(" $REPO ", ' bb-creds ') ==
                new ScmSettings(REPO, 'bb-creds', BASIC, null, null, null, [], null, null, null, null)
        new ScmSettings(REPO, 'bb', null, null, null, null, null, ' https://api.bitbucket.org/2.0 ', ' ta ', ' TA ',
                ' cert ') == new ScmSettings(REPO, 'bb', BASIC, null, null, null, [], 'https://api.bitbucket.org/2.0',
                'ta', 'TA', 'cert')
    }

    def "a repository writes every scm.bitbucket key that is set, and nothing without its URL"() {
        expect:
        written(ScmSettings.NONE) == [:]
        written(new ScmSettings(null, 'bb-creds', BEARER, CLOUD, 'main', 'ssh://git@x/r.git', ['alice'],
                'https://api.bitbucket.org/2.0', 'ta', 'TA', 'cert')) == [:]
        written(new ScmSettings(" $REPO ", 'bb-creds', BEARER, SERVER, ' develop ',
                ' ssh://git@bitbucket.bbh.com:7999/ta/cert.git ', ['alice', '{0b9e-uuid}'],
                ' https://bitbucket.bbh.com/rest/api/1.0 ', null, ' TA ', ' cert-scanner ')) ==
                [scm: [bitbucket: [url        : REPO, credentialsId: 'bb-creds', authType: 'bearer', type: 'server',
                                   targetBranch: 'develop', cloneUrl: 'ssh://git@bitbucket.bbh.com:7999/ta/cert.git',
                                   reviewers  : ['alice', '{0b9e-uuid}'],
                                   apiUrl     : 'https://bitbucket.bbh.com/rest/api/1.0', projectKey: 'TA',
                                   repoSlug   : 'cert-scanner']]]
        written(ScmSettings.of(REPO, 'bb-creds')) ==
                [scm: [bitbucket: [url: REPO, credentialsId: 'bb-creds', authType: 'basic']]]
        written(new ScmSettings(REPO, null, BASIC, CLOUD, null, null, [], null, 'ta-workspace', null, 'cert')).scm
                .bitbucket == [url: REPO, authType: 'basic', type: 'cloud', workspace: 'ta-workspace', repoSlug: 'cert']
    }

    def "a repository needs the credentials GoldenFix pushes with"() {
        expect:
        reported { ScmSettings.of(REPO, ' ').validate(it) }.collect { [it.field, it.message] } ==
                [['credentialsId', 'is required to push GoldenFix branches and open pull requests']]
        problems(ScmSettings.of(REPO, 'bb-creds')) == []
        problems(ScmSettings.NONE) == []
    }

    def "the Bitbucket repository keys need the repository URL they refine"() {
        expect:
        problems(new ScmSettings(null, null, null, null, null, null, null, apiUrl, workspace, projectKey, repoSlug)) ==
                ['repositoryUrl']
        messages(new ScmSettings(null, null, null, null, null, null, null, apiUrl, workspace, projectKey, repoSlug)) ==
                ['is required when the Bitbucket API URL, workspace, project key or repository slug is set']
        problems(new ScmSettings(REPO, 'bb', null, null, null, null, null, apiUrl, workspace, projectKey, repoSlug)) == []

        where:
        apiUrl                          | workspace | projectKey | repoSlug
        'https://api.bitbucket.org/2.0' | null      | null       | null
        null                            | 'ta'      | null       | null
        null                            | null      | 'TA'       | null
        null                            | null      | null       | 'cert'
    }

    def "metrics are on by default, use the test environment and default the project to the product code and service name"() {
        expect:
        new MetricsSettings(null, ' ', ' ') == MetricsSettings.DEFAULTS
        MetricsSettings.DEFAULTS == new MetricsSettings(true, null, 'test')
        new MetricsSettings(false, ' cert ', ' prod ') == new MetricsSettings(false, 'cert', 'prod')
        written(new MetricsSettings(null, 'CERT-gui', null)) == [influx: [enabled: true, project: 'CERT-gui', env: 'test']]
        written(new MetricsSettings(false, null, 'uat')) == [influx: [enabled: false, env: 'uat']]
        problems(MetricsSettings.DEFAULTS) == []
        MetricsSettings.DEFAULTS.withDefaultProject('CERT', 'gui') == new MetricsSettings(true, 'CERT-gui', 'test')
        new MetricsSettings(false, null, 'uat').withDefaultProject('CERT', 'gui') == new MetricsSettings(false, 'CERT-gui', 'uat')
        new MetricsSettings(true, 'cert-scanner', 'uat').with { it.withDefaultProject('CERT', 'gui').is(it) }
    }

    def "the AppScan account writes the key ID and the credential holding the secret"() {
        expect:
        written(new AppScanAccount(' bbh_key ', ' asoc-creds ')) == [asoc: [keyId: 'bbh_key', token: 'asoc-creds']]
        written(new AppScanAccount('bbh_key', ' ')) == [asoc: [keyId: 'bbh_key']]
        new AppScanAccount(' ', ' ') == new AppScanAccount(null, null)
        problems(new AppScanAccount('bbh_key', null)) == []
    }

    private static AppScanSettings dast(String targetUrl) {
        new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null, null, true, null, targetUrl, null)
    }
}
