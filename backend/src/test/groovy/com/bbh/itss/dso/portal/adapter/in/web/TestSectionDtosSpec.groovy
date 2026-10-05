package com.bbh.itss.dso.portal.adapter.in.web

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE

class TestSectionDtosSpec extends Specification {

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "test requests are normalised before they are validated"() {
        expect:
        new TestJobDto(SMOKE, ' ', null, ' CERT/smoke ', null, ' ', ' ', ' ', ' ') ==
                new TestJobDto(SMOKE, null, null, 'CERT/smoke', null, null, null, null, null)
        new TestJobDto(SMOKE, null, null, null, null, null, null, null, null).job() == null
        new UnitTestSettingsDto(null, ' ', ' ', ' ', null, ' ') ==
                new UnitTestSettingsDto(ToolCommandDto.NONE, null, null, null, false, null)
    }

    def "valid test sections pass bean validation"() {
        expect:
        validator.validate(section).isEmpty()

        where:
        section << [new TestSettingsDto(1, 100, 50, 2),
                    new TestJobDto(REGRESSION, 'r', REMOTE, 'CERT/r', 1440, 'A=1', 'qc', 'https://jenkins-qc', 'creds'),
                    new UnitTestSettingsDto(new ToolCommandDto(['test'], [], null, null, []), '**/*.xml', 'gui', 'out', true,
                            'jacoco.xml')]
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                       | section                                                                        || property
        'no parallel test job'            | new TestSettingsDto(0, null, null, null)                                       || 'maxParallel'
        'too many parallel smoke jobs'    | new TestSettingsDto(null, 101, null, null)                                     || 'smokeMaxParallel'
        'no parallel regression job'      | new TestSettingsDto(null, null, 0, null)                                       || 'regressionMaxParallel'
        'too many parallel performance'   | new TestSettingsDto(null, null, null, 101)                                     || 'performanceMaxParallel'
        'a job without a stage'           | new TestJobDto(null, null, null, 'CERT/smoke', null, null, null, null, null)   || 'stage'
        'a blank job'                     | new TestJobDto(SMOKE, null, null, '  ', null, null, null, null, null)          || 'job'
        'a missing job'                   | new TestJobDto(SMOKE, null, null, null, null, null, null, null, null)          || 'job'
        'a timeout of zero'               | new TestJobDto(SMOKE, null, null, 'CERT/s', 0, null, null, null, null)         || 'timeoutMinutes'
        'a timeout over a day'            | new TestJobDto(SMOKE, null, null, 'CERT/s', 1441, null, null, null, null)      || 'timeoutMinutes'
        'a too long job name'             | new TestJobDto(SMOKE, 'n' * 201, null, 'CERT/s', null, null, null, null, null) || 'name'
        'too long parameters'             | new TestJobDto(SMOKE, null, null, 'CERT/s', null, 'p' * 2001, null, null, null) || 'parameters'
        'a remote Jenkins that is no URL' | new TestJobDto(SMOKE, null, REMOTE, 'CERT/s', null, null, null, 'jenkins-qc', null) || 'remoteJenkinsUrl'
        'a too long credential'           | new TestJobDto(SMOKE, null, null, 'CERT/s', null, null, null, null, 'c' * 201) || 'credentialsId'
        'a too long result pattern'       | new UnitTestSettingsDto(null, 'r' * 501, null, null, false, null)              || 'resultPattern'
        'a too long coverage report path' | new UnitTestSettingsDto(null, null, null, null, false, 'c' * 501)              || 'coverageReportPath'
        'a unit test variable w/o value'  | new UnitTestSettingsDto(new ToolCommandDto(['test'], [], null, null, ['CI']), null, null, null, false, null) || 'command.environment[0].<list element>'
    }
}
