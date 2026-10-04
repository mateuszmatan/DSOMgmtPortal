package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.common.ValidationProblems
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.catalog.TestJobType.LOCAL
import static com.bbh.itss.dso.portal.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.catalog.TestStage.PERFORMANCE
import static com.bbh.itss.dso.portal.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.catalog.TestStage.SMOKE

class TestSectionsSpec extends Specification {

    static final String REMOTE_URL = 'https://jenkins-qc.bbh.com/job/CERT/job/regression/'

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "unit test settings trim their paths and allow no empty results by default"() {
        when:
        def unitTests = new UnitTestSettings(null, ' **/TEST-*.xml ', ' ', ' gui/build ', null, ' ')

        then:
        unitTests.command() == ToolCommand.NONE
        unitTests.resultPattern() == '**/TEST-*.xml'
        unitTests.rootDir() == null
        unitTests.reportOutDir() == 'gui/build'
        !unitTests.allowEmptyResults()
        unitTests.coverageReportPath() == null
        UnitTestSettings.NONE == new UnitTestSettings(ToolCommand.NONE, ' ', '', ' ', false, ' ')
    }

    def "a Gradle unit tests stage writes its command, its reports and the coverage report path"() {
        given:
        def unitTests = new UnitTestSettings(new ToolCommand(['test', 'jacocoTestReport'], ['--continue'], 'gui', null,
                ['CI=true']), '**/TEST-*.xml', 'gui', 'build/reports', true, 'build/reports/jacoco/test/jacocoTestReport.xml')

        expect:
        written { unitTests.writeTo(it, GRADLE) } ==
                [tests   : [unitTests: [gradle           : [tasks: ['test', 'jacocoTestReport'], flags: ['--continue'], dir: 'gui',
                                                            env  : [CI: 'true']],
                                        unitTestResult   : '**/TEST-*.xml', rootDir: 'gui', reportOutDir: 'build/reports',
                                        allowEmptyResults: true]],
                 coverage: [reportPath: 'build/reports/jacoco/test/jacocoTestReport.xml']]
    }

    def "a Maven unit tests stage writes its goals under tests.unitTests.maven"() {
        given:
        def unitTests = new UnitTestSettings(new ToolCommand(['test', 'jacoco:report'], [], null, '/opt/maven', []),
                null, null, null, false, 'target/site/jacoco/jacoco.xml')

        expect:
        written { unitTests.writeTo(it, MAVEN) } ==
                [tests   : [unitTests: [maven: [goals: ['test', 'jacoco:report'], mvnPath: '/opt/maven']]],
                 coverage: [reportPath: 'target/site/jacoco/jacoco.xml']]
    }

    def "a Flutter unit tests stage writes its reports but no Gradle or Maven command"() {
        given:
        def unitTests = new UnitTestSettings(ToolCommand.of(['test'], []), 'test-results/*.xml', null, null, false, null)

        expect:
        written { unitTests.writeTo(it, FLUTTER) } == [tests: [unitTests: [unitTestResult: 'test-results/*.xml']]]
    }

    def "unit tests that set nothing write nothing for #tool"() {
        expect:
        written { UnitTestSettings.NONE.writeTo(it, tool) } == [:]

        where:
        tool << BuildTool.values()
    }

    def "a #tool unit tests stage that sets #description reports #fields"() {
        expect:
        problems { unitTests.validate(it, tool) } == fields

        where:
        description                | tool    | unitTests                                                                      || fields
        'nothing'                  | GRADLE  | UnitTestSettings.NONE                                                          || []
        'only a coverage report'   | MAVEN   | new UnitTestSettings(null, null, null, null, false, 'jacoco.xml')              || []
        'a result pattern'         | GRADLE  | new UnitTestSettings(null, '**/TEST-*.xml', null, null, false, null)           || ['command.tasks']
        'a root directory'         | MAVEN   | new UnitTestSettings(null, null, 'gui', null, false, null)                     || ['command.tasks']
        'a report directory'       | GRADLE  | new UnitTestSettings(null, null, null, 'reports', false, null)                 || ['command.tasks']
        'empty results allowed'    | MAVEN   | new UnitTestSettings(null, null, null, null, true, null)                       || ['command.tasks']
        'flags without tasks'      | GRADLE  | new UnitTestSettings(ToolCommand.of([], ['--info']), null, null, null, false, null) || ['command.tasks']
        'tasks'                    | GRADLE  | new UnitTestSettings(ToolCommand.of(['test'], []), '**/*.xml', null, null, false, null) || []
        'a result pattern'         | FLUTTER | new UnitTestSettings(null, '**/TEST-*.xml', null, null, true, null)            || []
    }

    def "a unit tests stage without a command is explained in the words of the build tool"() {
        given:
        def unitTests = new UnitTestSettings(null, '**/TEST-*.xml', null, null, false, null)

        expect:
        messages { unitTests.validate(it, MAVEN) } == ['add the Maven goals of the unit tests, for example test jacoco:report']
        messages { unitTests.validate(it, GRADLE) } == ['add the Gradle tasks of the unit tests, for example test jacocoTestReport']
    }

    def "a test job trims its values and stores blank ones as null"() {
        when:
        def job = new TestJob(SMOKE, ' ', null, ' CERT/gui-smoke ', null, ' ', ' ', ' ', ' ')

        then:
        job.stage() == SMOKE
        job.name() == null
        job.type() == null
        job.job() == 'CERT/gui-smoke'
        job.timeoutMinutes() == null
        job.parameters() == null
        job.remoteJenkins() == null
        job.remoteJenkinsUrl() == null
        job.credentialsId() == null
        new TestJob(SMOKE, null, null, null, null, null, null, null, null).job() == null
    }

    def "a job given as #job is a URL: #url"() {
        expect:
        new TestJob(SMOKE, null, null, job, null, null, null, null, null).isUrl() == url

        where:
        job                                   || url
        'https://jenkins-qc.bbh.com/job/x/'   || true
        ' http://jenkins-qc:8080/job/x '      || true
        'CERT/gui-smoke'                      || false
        'folder/https-check'                  || false
        null                                  || false
    }

    def "a job path is written as job and only the fields that are set"() {
        expect:
        new TestJob(SMOKE, null, null, 'CERT/gui-smoke', null, null, null, null, null).toConfig() == [job: 'CERT/gui-smoke']
    }

    def "a job URL is written as url with every field in the library's order"() {
        when:
        def entry = new TestJob(REGRESSION, 'regression', REMOTE, REMOTE_URL, 90, 'ENV=qc\nBROWSER=chrome', 'jenkins-qc',
                'https://jenkins-qc.bbh.com', 'jenkins-qc-token').toConfig()

        then:
        entry == [name            : 'regression', type: 'remote', url: REMOTE_URL, timeoutMin: 90,
                  parameters      : 'ENV=qc\nBROWSER=chrome', remoteJenkins: 'jenkins-qc',
                  remoteJenkinsUrl: 'https://jenkins-qc.bbh.com', credentialsId: 'jenkins-qc-token']
        entry.keySet() as List == ['name', 'type', 'url', 'timeoutMin', 'parameters', 'remoteJenkins', 'remoteJenkinsUrl',
                                   'credentialsId']
        new TestJob(SMOKE, null, LOCAL, 'CERT/smoke', 15, null, null, null, null).toConfig() ==
                [type: 'local', job: 'CERT/smoke', timeoutMin: 15]
    }

    def "test stages and job types are written in lower case"() {
        expect:
        TestStage.values()*.configKey() == ['smoke', 'regression', 'performance']
        TestJobType.values()*.configValue() == ['local', 'remote']
    }

    def "default test settings without jobs write nothing"() {
        expect:
        TestSettings.DEFAULTS == new TestSettings(null, null, null, null)
        written { TestSettings.DEFAULTS.writeTo(it, []) } == [:]
    }

    def "each stage writes its own limit and its jobs in the order they were entered"() {
        given:
        def smoke1 = new TestJob(SMOKE, 'smoke-api', null, 'CERT/api-smoke', null, null, null, null, null)
        def regression = new TestJob(REGRESSION, null, REMOTE, REMOTE_URL, 60, null, null, null, null)
        def smoke2 = new TestJob(SMOKE, 'smoke-gui', null, 'CERT/gui-smoke', 10, null, null, null, null)

        when:
        def tests = written { new TestSettings(4, 2, null, 1).writeTo(it, [smoke1, regression, smoke2]) }.tests

        then:
        tests == [maxParallel: 4,
                  smoke      : [maxParallel: 2, jobs: [[name: 'smoke-api', job: 'CERT/api-smoke'],
                                                       [name: 'smoke-gui', job: 'CERT/gui-smoke', timeoutMin: 10]]],
                  regression : [jobs: [[type: 'remote', url: REMOTE_URL, timeoutMin: 60]]],
                  performance: [maxParallel: 1]]
        tests.keySet() as List == ['maxParallel', 'smoke', 'regression', 'performance']
    }

    def "a stage limit is written even when the stage has no jobs"() {
        expect:
        written { new TestSettings(null, null, 3, null).writeTo(it, []) } == [tests: [regression: [maxParallel: 3]]]
        written { new TestSettings(null, null, null, 5).writeTo(it, []) } == [tests: [performance: [maxParallel: 5]]]
        written {
            new TestSettings(null, null, null, null)
                    .writeTo(it, [new TestJob(PERFORMANCE, null, null, 'CERT/load', null, null, null, null, null)])
        } == [tests: [performance: [jobs: [[job: 'CERT/load']]]]]
    }

    def "valid test sections pass bean validation"() {
        expect:
        validator.validate(section).isEmpty()

        where:
        section << [new TestSettings(1, 100, 50, 2),
                    new TestJob(REGRESSION, 'r', REMOTE, 'CERT/r', 1440, 'A=1', 'qc', 'https://jenkins-qc', 'creds'),
                    new UnitTestSettings(ToolCommand.of(['test'], []), '**/*.xml', 'gui', 'out', true, 'jacoco.xml')]
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                       | section                                                                     || property
        'no parallel test job'            | new TestSettings(0, null, null, null)                                       || 'maxParallel'
        'too many parallel smoke jobs'    | new TestSettings(null, 101, null, null)                                     || 'smokeMaxParallel'
        'no parallel regression job'      | new TestSettings(null, null, 0, null)                                       || 'regressionMaxParallel'
        'too many parallel performance'   | new TestSettings(null, null, null, 101)                                     || 'performanceMaxParallel'
        'a job without a stage'           | new TestJob(null, null, null, 'CERT/smoke', null, null, null, null, null)   || 'stage'
        'a blank job'                     | new TestJob(SMOKE, null, null, '  ', null, null, null, null, null)          || 'job'
        'a missing job'                   | new TestJob(SMOKE, null, null, null, null, null, null, null, null)          || 'job'
        'a timeout of zero'               | new TestJob(SMOKE, null, null, 'CERT/s', 0, null, null, null, null)         || 'timeoutMinutes'
        'a timeout over a day'            | new TestJob(SMOKE, null, null, 'CERT/s', 1441, null, null, null, null)      || 'timeoutMinutes'
        'a too long job name'             | new TestJob(SMOKE, 'n' * 201, null, 'CERT/s', null, null, null, null, null) || 'name'
        'too long parameters'             | new TestJob(SMOKE, null, null, 'CERT/s', null, 'p' * 2001, null, null, null) || 'parameters'
        'a remote Jenkins that is no URL' | new TestJob(SMOKE, null, REMOTE, 'CERT/s', null, null, null, 'jenkins-qc', null) || 'remoteJenkinsUrl'
        'a too long credential'           | new TestJob(SMOKE, null, null, 'CERT/s', null, null, null, null, 'c' * 201) || 'credentialsId'
        'a too long result pattern'       | new UnitTestSettings(null, 'r' * 501, null, null, false, null)              || 'resultPattern'
        'a too long coverage report path' | new UnitTestSettings(null, null, null, null, false, 'c' * 501)              || 'coverageReportPath'
        'a unit test variable w/o value'  | new UnitTestSettings(new ToolCommand(['test'], [], null, null, ['CI']), null, null, null, false, null) || 'command.environment[0].<list element>'
    }

    private static Map written(Closure write) {
        def tree = new ConfigTree()
        write(tree)
        tree.toMap()
    }

    private static List<String> problems(Closure validate) {
        reported(validate)*.field
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
