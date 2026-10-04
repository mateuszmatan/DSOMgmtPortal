package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.common.ValidationProblems
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.dso.portal.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.dso.portal.catalog.BitbucketType.CLOUD
import static com.bbh.itss.dso.portal.catalog.BitbucketType.SERVER
import static com.bbh.itss.dso.portal.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.catalog.DeployTarget.VM

class ConfigSectionsSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "build settings default the source directory, trim the paths and start without a command"() {
        when:
        def build = new BuildSettings(MAVEN, ' ', ' /opt/jdk-17 ', null, ' target/cert.jar ', null)

        then:
        build.tool() == MAVEN
        build.sourceDir() == '.'
        build.javaPath() == '/opt/jdk-17'
        !build.autoSetup()
        build.buildPath() == 'target/cert.jar'
        build.command() == ToolCommand.NONE
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

    def "automatic build tool setup is written only when it is enabled"() {
        expect:
        written(new BuildSettings(GRADLE, 'app', null, true, null, ToolCommand.of(['build'], []))) ==
                [buildTool: 'gradle', sourceDir: 'app', buildToolAutoSetup: true, build: [gradle: [tasks: ['build']]]]
        written(new BuildSettings(GRADLE, 'app', '/jdk', false, null, ToolCommand.of(['build'], []))) ==
                [buildTool: 'gradle', sourceDir: 'app', javaPath: '/jdk', build: [gradle: [tasks: ['build']]]]
    }

    def "a Flutter build writes its artifact path but no Gradle or Maven command"() {
        expect:
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
        FLUTTER | null     | false     | []        || []
    }

    def "a missing build command is explained in the words of the build tool"() {
        expect:
        messages(new BuildSettings(MAVEN, null, '/jdk', false, null, null)) ==
                ['add the Maven goals of the build, for example clean verify']
        messages(new BuildSettings(GRADLE, null, '/jdk', false, null, null)) ==
                ['add the Gradle tasks of the build, for example clean build']
        messages(new BuildSettings(GRADLE, null, null, false, null, ToolCommand.of(['build'], []))) ==
                ['set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them']
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
        deployment.appName() == 'gui'
        deployment.artifactName() == 'gui.jar'
        deployment.baseArtifactName() == 'gui-1.0.0.jar'
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
        appScan.applicationId() == APP_ID
        appScan.sastScanName() == null
        appScan.includedDirs() == ['src']
        appScan.excludedDirs() == []
        appScan.compile()
        !appScan.sourceCodeOnly()
        !appScan.useConfigFile()
        !appScan.insecureTls()
        appScan.clientPath() == null
        appScan.compileCommand() == ToolCommand.NONE
        !appScan.dastEnabled()
        appScan.dastScanName() == null
        appScan.dastTargetUrl() == null
        appScan.dastPresenceId() == null
        written { appScan.writeTo(it, GRADLE) } == [appId: APP_ID, includedDirs: 'src', dast: [enabled: false]]
    }

    def "AppScan settings made of the application only equal the defaults"() {
        expect:
        AppScanSettings.of(APP_ID) == new AppScanSettings(APP_ID, null, null, null, null, null, null, null, null, null,
                null, null, null, null)
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
        problems { new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null, null, true, null, null, null).validate(it) } ==
                ['dastTargetUrl']
        messages { new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null, null, true, null, null, null).validate(it) } ==
                ['is required when DAST is enabled']
        problems { new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null, null, true, null, 'https://x', null).validate(it) } ==
                []
        problems { AppScanSettings.of(APP_ID).validate(it) } == []
    }

    def "SonarQube settings trim their values and switch the badges off by default"() {
        when:
        def sonar = new SonarSettings(' ', ' cert ', ' ', ' ', ' ', ' ', null, null, null)

        then:
        sonar.projectName() == null
        sonar.projectKey() == 'cert'
        sonar.installationName() == null
        sonar.credentialsId() == null
        sonar.authTokenCredentialsId() == null
        sonar.badgeToken() == null
        !sonar.addBadges()
        !sonar.fullBadges()
        sonar.command() == ToolCommand.NONE
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

    def "a missing analysis command is explained in the words of the build tool"() {
        expect:
        messages { SonarSettings.of(null, 'cert', null).validate(it, MAVEN) } ==
                ['add the Maven goals of the analysis, for example sonar:sonar']
        messages { SonarSettings.of(null, 'cert', null).validate(it, GRADLE) } ==
                ['add the Gradle tasks of the analysis, for example sonarqube']
    }

    def "Nexus IQ settings keep each scan pattern once and default the stage to build"() {
        when:
        def nexusIq = new NexusIqSettings(' cert ', ['**/*.jar', ' ', '**/*.jar', ' **/*.war '], ' ', null, ' ')

        then:
        nexusIq.application() == 'cert'
        nexusIq.scanPatterns() == ['**/*.jar', '**/*.war']
        nexusIq.stage() == 'build'
        !nexusIq.failOnNetworkError()
        nexusIq.scaScanName() == null
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
        def scm = new ScmSettings(' ', ' ', null, null, ' ', ' ', [' alice ', 'alice', '', 'bob'])

        then:
        scm.repositoryUrl() == null
        scm.credentialsId() == null
        scm.authType() == BASIC
        scm.type() == null
        scm.targetBranch() == null
        scm.cloneUrl() == null
        scm.reviewers() == ['alice', 'bob']
        ScmSettings.NONE == new ScmSettings(null, null, null, null, null, null, null)
        ScmSettings.of(" $REPO ", ' bb-creds ') == new ScmSettings(REPO, 'bb-creds', BASIC, null, null, null, [])
    }

    def "nothing of the repository is written without its URL"() {
        expect:
        written(ScmSettings.NONE) == [:]
        written(new ScmSettings(null, 'bb-creds', BEARER, CLOUD, 'main', 'ssh://git@x/r.git', ['alice'])) == [:]
    }

    def "a repository writes every scm.bitbucket key that is set"() {
        expect:
        written(new ScmSettings(" $REPO ", 'bb-creds', BEARER, SERVER, ' develop ',
                ' ssh://git@bitbucket.bbh.com:7999/ta/cert.git ', ['alice', '{0b9e-uuid}'])) ==
                [scm: [bitbucket: [url        : REPO, credentialsId: 'bb-creds', authType: 'bearer', type: 'server',
                                   targetBranch: 'develop', cloneUrl: 'ssh://git@bitbucket.bbh.com:7999/ta/cert.git',
                                   reviewers  : ['alice', '{0b9e-uuid}']]]]
        written(ScmSettings.of(REPO, 'bb-creds')) ==
                [scm: [bitbucket: [url: REPO, credentialsId: 'bb-creds', authType: 'basic']]]
        written(new ScmSettings(REPO, null, BASIC, CLOUD, null, null, [])).scm.bitbucket ==
                [url: REPO, authType: 'basic', type: 'cloud']
    }

    def "a repository needs the credentials GoldenFix pushes with"() {
        expect:
        problems(ScmSettings.of(REPO, ' ')) == ['credentialsId']
        messages(ScmSettings.of(REPO, null)) == ['is required to push GoldenFix branches and open pull requests']
        problems(ScmSettings.of(REPO, 'bb-creds')) == []
        problems(ScmSettings.NONE) == []
    }

    def "metrics are on by default and use the test environment"() {
        expect:
        MetricsSettings.DEFAULTS.enabled()
        MetricsSettings.DEFAULTS.influxProject() == null
        MetricsSettings.DEFAULTS.influxEnv() == 'test'
        new MetricsSettings(null, ' ', ' ') == MetricsSettings.DEFAULTS
        new MetricsSettings(false, ' cert ', ' prod ') == new MetricsSettings(false, 'cert', 'prod')
        written(new MetricsSettings(null, 'CERT-gui', null)) == [influx: [enabled: true, project: 'CERT-gui', env: 'test']]
        written(new MetricsSettings(false, null, 'uat')) == [influx: [enabled: false, env: 'uat']]
        problems(MetricsSettings.DEFAULTS) == []
    }

    def "a missing metrics project defaults to the product code and service name"() {
        given:
        def explicit = new MetricsSettings(true, 'cert-scanner', 'uat')

        expect:
        MetricsSettings.DEFAULTS.withDefaultProject('CERT', 'gui') == new MetricsSettings(true, 'CERT-gui', 'test')
        new MetricsSettings(false, null, 'uat').withDefaultProject('CERT', 'gui') == new MetricsSettings(false, 'CERT-gui', 'uat')
        explicit.withDefaultProject('CERT', 'gui').is(explicit)
    }

    def "the AppScan account writes the key ID and the credential holding the secret"() {
        expect:
        written(new AppScanAccount(' bbh_key ', ' asoc-creds ')) == [asoc: [keyId: 'bbh_key', token: 'asoc-creds']]
        written(new AppScanAccount('bbh_key', ' ')) == [asoc: [keyId: 'bbh_key']]
        new AppScanAccount(' ', ' ') == new AppScanAccount(null, null)
        problems(new AppScanAccount('bbh_key', null)) == []
    }

    def "enumerated settings are written in lower case"() {
        expect:
        BuildTool.values()*.configValue() == ['gradle', 'maven', 'flutter']
        DeployTarget.values()*.configValue() == ['vm', 'openshift']
        BitbucketAuthType.values()*.configValue() == ['basic', 'bearer']
        BitbucketType.values()*.configValue() == ['server', 'cloud']
    }

    def "valid sections pass bean validation"() {
        expect:
        validator.validate(section).isEmpty()

        where:
        section << [
                new BuildSettings(MAVEN, 'gui', '/jdk', true, 'target/gui.jar',
                        new ToolCommand(['clean', 'verify'], ['-B'], 'gui', '/opt/maven', ['A=1', 'B_2=x=y'])),
                new DeploymentSettings(OPENSHIFT, 'gui', 'gui.jar', 'gui-1.0.jar'),
                new AppScanSettings(APP_ID.toUpperCase(), 'cert', ['src'], ['test'], true, false, false, false, '/opt',
                        null, true, 'dast', 'https://rdl1.testbbh.com', 'p-1'),
                new SonarSettings('Cert', 'bbh:cert-scanner_1.0', 'SonarQube', 'c', 't', 'sqb_1a2B', true, false, null),
                new NexusIqSettings('cert', ['**/*.jar'], 'stage-release', true, 'sca'),
                new ScmSettings(REPO, 'bb', BEARER, SERVER, 'main', 'ssh://git@bitbucket.bbh.com/ta/cert.git', ['alice']),
                new MetricsSettings(true, 'CERT-gui_1.0', 'qc-2'),
                new AppScanAccount('bbh_key', 'asoc-creds')]
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                        | section                                                                                         || property
        'a missing build tool'             | new BuildSettings(null, null, '/jdk', false, null, null)                                        || 'tool'
        'a too long source directory'      | new BuildSettings(GRADLE, 'd' * 501, null, true, null, null)                                    || 'sourceDir'
        'a too long artifact path'         | new BuildSettings(GRADLE, null, null, true, 'p' * 501, null)                                    || 'buildPath'
        'more than 30 build tasks'         | new BuildSettings(GRADLE, null, null, true, null, ToolCommand.of(tasks(31), []))                || 'command.tasks'
        'a build variable without a value' | new BuildSettings(GRADLE, null, null, true, null, new ToolCommand([], [], null, null, ['JAVA'])) || 'command.environment[0].<list element>'
        'a missing deploy target'          | new DeploymentSettings(null, null, null, null)                                                  || 'target'
        'a too long base artifact name'    | new DeploymentSettings(VM, null, null, 'a' * 301)                                               || 'baseArtifactName'
        'a missing AppScan ID'             | appScan(null)                                                                                   || 'applicationId'
        'an AppScan ID that is no UUID'    | appScan('not-a-uuid')                                                                           || 'applicationId'
        'a folder with a comma'            | appScanDirs(['src,lib'], [])                                                                    || 'includedDirs[0].<list element>'
        'more than 30 excluded folders'    | appScanDirs([], tasks(31))                                                                      || 'excludedDirs'
        'a DAST URL without http'          | appScanDast('ftp://rdl1.testbbh.com')                                                           || 'dastTargetUrl'
        'a too long AppScan client path'   | appScanClient('c' * 501)                                                                        || 'clientPath'
        'a compile flag that is too long'  | appScanCompile(ToolCommand.of([], ['f' * 301]))                                                 || 'compileCommand.flags[0].<list element>'
        'a SonarQube key of digits'        | SonarSettings.of(null, '1234', null)                                                            || 'projectKey'
        'a SonarQube key with a space'     | SonarSettings.of(null, 'cert scanner', null)                                                    || 'projectKey'
        'a badge token with a dash'        | new SonarSettings(null, null, null, null, null, 'sqb-1', false, false, null)                    || 'badgeToken'
        'a too long SonarQube project'     | SonarSettings.of('n' * 201, null, null)                                                         || 'projectName'
        'more than 20 scan patterns'       | NexusIqSettings.of(null, tasks(21))                                                             || 'scanPatterns'
        'a Nexus IQ stage in upper case'   | new NexusIqSettings(null, [], 'Release', false, null)                                           || 'stage'
        'a repository that is no URL'      | ScmSettings.of('bitbucket', 'bb')                                                               || 'repositoryUrl'
        'a clone URL in scp form'          | new ScmSettings(REPO, 'bb', BASIC, null, null, 'git@bitbucket:ta/cert.git', [])                 || 'cloneUrl'
        'a reviewer with a space'          | new ScmSettings(REPO, 'bb', BASIC, null, null, null, ['john doe'])                              || 'reviewers[0].<list element>'
        'more than 20 reviewers'           | new ScmSettings(REPO, 'bb', BASIC, null, null, null, tasks(21))                                 || 'reviewers'
        'a metrics tag with a space'       | new MetricsSettings(true, 'cert scanner', null)                                                 || 'influxProject'
        'a metrics environment with /'     | new MetricsSettings(true, null, 'qc/1')                                                         || 'influxEnv'
        'a missing AppScan key ID'         | new AppScanAccount(' ', null)                                                                   || 'keyId'
        'a too long secret credential'     | new AppScanAccount('key', 's' * 201)                                                            || 'secretCredentialsId'
    }

    private static List<String> tasks(int count) {
        (1..count).collect { "t$it" as String }
    }

    private static AppScanSettings appScan(String applicationId) {
        new AppScanSettings(applicationId, null, [], [], true, false, false, false, null, null, false, null, null, null)
    }

    private static AppScanSettings appScanDirs(List<String> included, List<String> excluded) {
        new AppScanSettings(APP_ID, null, included, excluded, true, false, false, false, null, null, false, null, null, null)
    }

    private static AppScanSettings appScanDast(String url) {
        new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null, null, true, null, url, null)
    }

    private static AppScanSettings appScanClient(String clientPath) {
        new AppScanSettings(APP_ID, null, [], [], true, false, false, false, clientPath, null, false, null, null, null)
    }

    private static AppScanSettings appScanCompile(ToolCommand command) {
        new AppScanSettings(APP_ID, null, [], [], true, false, false, false, null, command, false, null, null, null)
    }

    private static Map written(ConfigSection section) {
        written { section.writeTo(it) }
    }

    private static Map written(Closure write) {
        def tree = new ConfigTree()
        write(tree)
        tree.toMap()
    }

    private static List<String> problems(ConfigSection section) {
        problems { section.validate(it) }
    }

    private static List<String> problems(Closure validate) {
        reported(validate)*.field
    }

    private static List<String> messages(ConfigSection section) {
        messages { section.validate(it) }
    }

    private static List<String> messages(Closure validate) {
        reported(validate)*.message
    }

    private static List reported(Closure validate) {
        def problems = new ValidationProblems()
        validate(problems)
        problems.list()
    }
}
