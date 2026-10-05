package com.bbh.itss.dso.portal.adapter.in.web

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.dso.portal.domain.catalog.BitbucketType.SERVER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM

class ConfigSectionDtosSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "request sections are normalised before they are validated, as the stored sections are"() {
        expect:
        new BuildSettingsDto(GRADLE, ' ', ' /jdk ', null, ' ', null) ==
                new BuildSettingsDto(GRADLE, '.', '/jdk', false, null, ToolCommandDto.NONE)
        new AppScanSettingsDto(" ${APP_ID.toUpperCase()} ", ' ', [' src ', '', 'src'], null, null, null, null, null,
                ' ', null, null, ' ', ' ', ' ') ==
                new AppScanSettingsDto(APP_ID, null, ['src'], [], true, false, false, false, null, ToolCommandDto.NONE,
                        false, null, null, null)
        new SonarSettingsDto(' Cert ', ' cert ', ' ', ' ', ' ', ' ', null, null, null) ==
                new SonarSettingsDto('Cert', 'cert', null, null, null, null, false, false, ToolCommandDto.NONE)
        new NexusIqSettingsDto(' cert ', [' a ', 'a', ' '], ' ', null, ' ') ==
                new NexusIqSettingsDto('cert', ['a'], 'build', false, null)
        new ScmSettingsDto(" $REPO ", ' bb ', null, null, ' ', ' ', [' alice ', 'alice'], ' ', ' cert ', ' ', ' gui ') ==
                new ScmSettingsDto(REPO, 'bb', BASIC, null, null, null, ['alice'], null, 'cert', null, 'gui')
        new MetricsSettingsDto(null, ' ', ' ') == new MetricsSettingsDto(true, null, 'test')
        new AppScanAccountDto(' key ', ' ') == new AppScanAccountDto('key', null)
        new DeploymentSettingsDto(VM, ' ', ' ', ' ') == new DeploymentSettingsDto(VM, null, null, null)
    }

    def "valid sections pass bean validation"() {
        expect:
        validator.validate(section).isEmpty()

        where:
        section << [
                new BuildSettingsDto(MAVEN, 'gui', '/jdk', true, 'target/gui.jar',
                        new ToolCommandDto(['clean', 'verify'], ['-B'], 'gui', '/opt/maven', ['A=1', 'B_2=x=y'])),
                new DeploymentSettingsDto(OPENSHIFT, 'gui', 'gui.jar', 'gui-1.0.jar'),
                new AppScanSettingsDto(APP_ID.toUpperCase(), 'cert', ['src'], ['test'], true, false, false, false, '/opt',
                        null, true, 'dast', 'https://rdl1.testbbh.com', 'p-1'),
                new SonarSettingsDto('Cert', 'bbh:cert-scanner_1.0', 'SonarQube', 'c', 't', 'sqb_1a2B', true, false, null),
                new NexusIqSettingsDto('cert', ['**/*.jar'], 'stage-release', true, 'sca'),
                new ScmSettingsDto(REPO, 'bb', BEARER, SERVER, 'main', 'ssh://git@bitbucket.bbh.com/ta/cert.git', ['alice'],
                        'https://bitbucket.bbh.com/rest/api/1.0', 'ta-workspace', '~JDOE', 'cert.scanner_1'),
                new MetricsSettingsDto(true, 'CERT-gui_1.0', 'qc-2'),
                new AppScanAccountDto('bbh_key', 'asoc-creds')]
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                        | section                                                                                   || property
        'a missing build tool'             | new BuildSettingsDto(null, null, '/jdk', false, null, null)                                || 'tool'
        'a too long source directory'      | new BuildSettingsDto(GRADLE, 'd' * 501, null, true, null, null)                            || 'sourceDir'
        'a too long artifact path'         | new BuildSettingsDto(GRADLE, null, null, true, 'p' * 501, null)                            || 'buildPath'
        'more than 30 build tasks'         | new BuildSettingsDto(GRADLE, null, null, true, null, command(tasks(31), []))               || 'command.tasks'
        'a build variable without a value' | new BuildSettingsDto(GRADLE, null, null, true, null, new ToolCommandDto([], [], null, null, ['JAVA'])) || 'command.environment[0].<list element>'
        'a missing deploy target'          | new DeploymentSettingsDto(null, null, null, null)                                          || 'target'
        'a too long base artifact name'    | new DeploymentSettingsDto(VM, null, null, 'a' * 301)                                       || 'baseArtifactName'
        'a missing AppScan ID'             | appScan(null)                                                                              || 'applicationId'
        'an AppScan ID that is no UUID'    | appScan('not-a-uuid')                                                                      || 'applicationId'
        'a folder with a comma'            | appScanDirs(['src,lib'], [])                                                               || 'includedDirs[0].<list element>'
        'more than 30 excluded folders'    | appScanDirs([], tasks(31))                                                                 || 'excludedDirs'
        'a DAST URL without http'          | appScanDast('ftp://rdl1.testbbh.com')                                                      || 'dastTargetUrl'
        'a too long AppScan client path'   | appScanClient('c' * 501)                                                                   || 'clientPath'
        'a compile flag that is too long'  | appScanCompile(command([], ['f' * 301]))                                                   || 'compileCommand.flags[0].<list element>'
        'a SonarQube key of digits'        | sonar(null, '1234')                                                                        || 'projectKey'
        'a SonarQube key with a space'     | sonar(null, 'cert scanner')                                                                || 'projectKey'
        'a badge token with a dash'        | new SonarSettingsDto(null, null, null, null, null, 'sqb-1', false, false, null)             || 'badgeToken'
        'a too long SonarQube project'     | sonar('n' * 201, null)                                                                     || 'projectName'
        'more than 20 scan patterns'       | new NexusIqSettingsDto(null, tasks(21), null, null, null)                                  || 'scanPatterns'
        'a Nexus IQ stage in upper case'   | new NexusIqSettingsDto(null, [], 'Release', false, null)                                   || 'stage'
        'a repository that is no URL'      | scm('bitbucket', null, [])                                                                 || 'repositoryUrl'
        'a clone URL in scp form'          | scm(REPO, 'git@bitbucket:ta/cert.git', [])                                                 || 'cloneUrl'
        'a reviewer with a space'          | scm(REPO, null, ['john doe'])                                                              || 'reviewers[0].<list element>'
        'more than 20 reviewers'           | scm(REPO, null, tasks(21))                                                                 || 'reviewers'
        'a Bitbucket API URL without http' | bitbucket('ftp://bitbucket.bbh.com', null, null, null)                                     || 'apiUrl'
        'a too long Bitbucket API URL'     | bitbucket('https://' + 'b' * 994, null, null, null)                                        || 'apiUrl'
        'a workspace with a space'         | bitbucket(null, 'ta workspace', null, null)                                                || 'workspace'
        'a too long workspace'             | bitbucket(null, 'w' * 201, null, null)                                                     || 'workspace'
        'a project key with a tab'         | bitbucket(null, null, 'T\tA', null)                                                        || 'projectKey'
        'a too long project key'           | bitbucket(null, null, 'K' * 201, null)                                                     || 'projectKey'
        'a repository slug with a space'   | bitbucket(null, null, null, 'cert scanner')                                                || 'repoSlug'
        'a too long repository slug'       | bitbucket(null, null, null, 's' * 201)                                                     || 'repoSlug'
        'a metrics tag with a space'       | new MetricsSettingsDto(true, 'cert scanner', null)                                         || 'influxProject'
        'a metrics environment with /'     | new MetricsSettingsDto(true, null, 'qc/1')                                                 || 'influxEnv'
        'a missing AppScan key ID'         | new AppScanAccountDto(' ', null)                                                           || 'keyId'
        'a too long secret credential'     | new AppScanAccountDto('key', 's' * 201)                                                    || 'secretCredentialsId'
    }

    def "the URL rules explain what they expect"() {
        expect:
        validator.validate(appScanDast('ftp://rdl1.testbbh.com'))*.message == ['must be an http or https URL']
        validator.validate(scm(REPO, 'git@bitbucket:ta/cert.git', []))*.message == ['must be an http, https or ssh URL']
        validator.validate(bitbucket('bitbucket.bbh.com', null, null, null))*.message == ['must be an http or https URL']
        validator.validate(bitbucket(null, 'ta workspace', null, null))*.message == ['must not contain whitespace']
    }

    private static ScmSettingsDto scm(String repositoryUrl, String cloneUrl, List<String> reviewers) {
        new ScmSettingsDto(repositoryUrl, 'bb', BASIC, null, null, cloneUrl, reviewers, null, null, null, null)
    }

    private static ScmSettingsDto bitbucket(String apiUrl, String workspace, String projectKey, String repoSlug) {
        new ScmSettingsDto(REPO, 'bb', BASIC, null, null, null, [], apiUrl, workspace, projectKey, repoSlug)
    }

    private static List<String> tasks(int count) {
        (1..count).collect { "t$it" as String }
    }

    private static ToolCommandDto command(List<String> tasks, List<String> flags) {
        new ToolCommandDto(tasks, flags, null, null, [])
    }

    private static SonarSettingsDto sonar(String name, String key) {
        new SonarSettingsDto(name, key, null, null, null, null, false, false, null)
    }

    private static AppScanSettingsDto appScan(String applicationId) {
        new AppScanSettingsDto(applicationId, null, [], [], true, false, false, false, null, null, false, null, null, null)
    }

    private static AppScanSettingsDto appScanDirs(List<String> included, List<String> excluded) {
        new AppScanSettingsDto(APP_ID, null, included, excluded, true, false, false, false, null, null, false, null, null,
                null)
    }

    private static AppScanSettingsDto appScanDast(String url) {
        new AppScanSettingsDto(APP_ID, null, [], [], true, false, false, false, null, null, true, null, url, null)
    }

    private static AppScanSettingsDto appScanClient(String clientPath) {
        new AppScanSettingsDto(APP_ID, null, [], [], true, false, false, false, clientPath, null, false, null, null, null)
    }

    private static AppScanSettingsDto appScanCompile(ToolCommandDto command) {
        new AppScanSettingsDto(APP_ID, null, [], [], true, false, false, false, null, command, false, null, null, null)
    }
}
