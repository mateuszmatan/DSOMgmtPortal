package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.domain.catalog.ToolCommand
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

class ToolCommandDtoSpec extends Specification {

    static final ToolCommandDto FULL = new ToolCommandDto(['clean', 'build'], ['--no-daemon', '-x', 'test'], 'gui',
            '/opt/maven-3.9', ['JAVA_OPTS=-Xmx2g', 'CI=true'])

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "a command request drops blank tokens, trims the rest and keeps repeated ones"() {
        expect:
        new ToolCommandDto([' clean ', ' ', 'clean'], null, ' ', ' ', null) ==
                new ToolCommandDto(['clean', 'clean'], [], null, null, [])
    }

    def "a command goes to the domain and back unchanged"() {
        expect:
        ToolCommandDto.from(FULL.toDomain()) == FULL
        FULL.toDomain() == new ToolCommand(['clean', 'build'], ['--no-daemon', '-x', 'test'], 'gui', '/opt/maven-3.9',
                ['JAVA_OPTS=-Xmx2g', 'CI=true'])
        ToolCommandDto.NONE.toDomain() == ToolCommand.NONE
    }

    def "bean validation accepts a full command"() {
        expect:
        validator.validate(FULL).isEmpty()
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(command)*.propertyPath*.toString() == [property]

        where:
        description                      | command                                                                       || property
        'more than 30 tasks'             | new ToolCommandDto((1..31).collect { "t$it" as String }, [], null, null, [])  || 'tasks'
        'a task longer than 200'         | new ToolCommandDto(['t' * 201], [], null, null, [])                           || 'tasks[0].<list element>'
        'more than 40 flags'             | new ToolCommandDto([], (1..41).collect { "-f$it" as String }, null, null, []) || 'flags'
        'a flag longer than 300'         | new ToolCommandDto([], ['f' * 301], null, null, [])                           || 'flags[0].<list element>'
        'a directory longer than 500'    | new ToolCommandDto([], [], 'd' * 501, null, [])                               || 'directory'
        'a Maven home longer than 500'   | new ToolCommandDto([], [], null, 'm' * 501, [])                               || 'mavenHome'
        'more than 30 variables'         | new ToolCommandDto([], [], null, null, (1..31).collect { "V$it=1" as String }) || 'environment'
        'a variable without a value'     | new ToolCommandDto([], [], null, null, ['CI=true', 'JAVA_HOME'])              || 'environment[1].<list element>'
        'a variable starting with digit' | new ToolCommandDto([], [], null, null, ['1X=y'])                              || 'environment[0].<list element>'
    }

    def "a variable without a value is explained"() {
        expect:
        validator.validate(new ToolCommandDto([], [], null, null, ['JAVA_HOME']))*.message == ['write each variable as NAME=value']
    }
}
