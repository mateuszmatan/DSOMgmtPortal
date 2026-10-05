package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.LOCAL
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.PERFORMANCE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.dso.portal.domain.shared.Sections.messages
import static com.bbh.itss.dso.portal.domain.shared.Sections.problems
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class TestSectionsSpec extends Specification {

    static final String REMOTE_URL = 'https://jenkins-qc.bbh.com/job/CERT/job/regression/'

    def "unit test settings trim their paths and allow no empty results by default"() {
        expect:
        new UnitTestSettings(null, ' **/TEST-*.xml ', ' ', ' gui/build ', null, ' ') ==
                new UnitTestSettings(ToolCommand.NONE, '**/TEST-*.xml', null, 'gui/build', false, null)
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

    def "a #tool unit tests stage writes #config"() {
        expect:
        written { unitTests.writeTo(it, tool) } == config

        where:
        tool << [MAVEN, FLUTTER, GRADLE, MAVEN, FLUTTER]
        unitTests << [new UnitTestSettings(new ToolCommand(['test', 'jacoco:report'], [], null, '/opt/maven', []), null, null,
                null, false, 'target/site/jacoco/jacoco.xml'),
                      new UnitTestSettings(ToolCommand.of(['test'], []), 'test-results/*.xml', null, null, false, null),
                      UnitTestSettings.NONE, UnitTestSettings.NONE, UnitTestSettings.NONE]
        config << [[tests   : [unitTests: [maven: [goals: ['test', 'jacoco:report'], mvnPath: '/opt/maven']]],
                    coverage: [reportPath: 'target/site/jacoco/jacoco.xml']],
                   [tests: [unitTests: [unitTestResult: 'test-results/*.xml']]], [:], [:], [:]]
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
        expect:
        new TestJob(SMOKE, ' ', null, ' CERT/gui-smoke ', null, ' ', ' ', ' ', ' ') ==
                new TestJob(SMOKE, null, null, 'CERT/gui-smoke', null, null, null, null, null)
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

    def "a #type job given as #job with remote Jenkins #remote and URL #remoteUrl must name its Jenkins: #needed"() {
        expect:
        new TestJob(SMOKE, null, type, job, null, null, remote, remoteUrl, null).needsRemoteJenkins() == needed

        where:
        type   | job                          | remote | remoteUrl            || needed
        REMOTE | 'CERT/smoke'                 | null   | null                 || true
        REMOTE | 'https://jenkins.qc/job/s/'  | null   | null                 || false
        REMOTE | 'CERT/smoke'                 | 'qc'   | null                 || false
        REMOTE | 'CERT/smoke'                 | null   | 'https://jenkins.qc' || false
        LOCAL  | 'CERT/smoke'                 | null   | null                 || false
        null   | 'CERT/smoke'                 | null   | null                 || false
    }

    def "a job URL is written as url, a path as job, with only the fields that are set in the library's order"() {
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
        new TestJob(SMOKE, null, null, 'CERT/gui-smoke', null, null, null, null, null).toConfig() == [job: 'CERT/gui-smoke']
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

    def "a stage limit is written even when the stage has no jobs, and the defaults without jobs write nothing"() {
        expect:
        TestStage.values()*.configKey() == ['smoke', 'regression', 'performance']
        TestSettings.DEFAULTS == new TestSettings(null, null, null, null)
        written { TestSettings.DEFAULTS.writeTo(it, []) } == [:]
        written { new TestSettings(null, null, 3, null).writeTo(it, []) } == [tests: [regression: [maxParallel: 3]]]
        written { new TestSettings(null, null, null, 5).writeTo(it, []) } == [tests: [performance: [maxParallel: 5]]]
        written {
            new TestSettings(null, null, null, null)
                    .writeTo(it, [new TestJob(PERFORMANCE, null, null, 'CERT/load', null, null, null, null, null)])
        } == [tests: [performance: [jobs: [[job: 'CERT/load']]]]]
    }
}
