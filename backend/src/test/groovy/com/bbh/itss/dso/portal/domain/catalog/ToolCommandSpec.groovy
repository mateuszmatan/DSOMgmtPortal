package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class ToolCommandSpec extends Specification {

    static final ToolCommand FULL = new ToolCommand(['clean', 'build'], ['--no-daemon', '-x', 'test'], 'gui',
            '/opt/maven-3.9', ['JAVA_OPTS=-Xmx2g', 'CI=true'], null, false)

    def "a command trims its values, keeps repeated tokens in their order and may be left out entirely"() {
        expect:
        new ToolCommand([' clean ', '', null, 'build', 'clean'], [' -s ', 'settings.xml', ' -gs ', 'settings.xml', ' '],
                ' gui ', ' ', [' CI=true ', ' '], null, false) ==
                new ToolCommand(['clean', 'build', 'clean'], ['-s', 'settings.xml', '-gs', 'settings.xml'], 'gui', null, ['CI=true'], null, false)
        new ToolCommand(null, null, null, null, null, null, false) == ToolCommand.NONE
        ToolCommand.NONE == new ToolCommand([], [], null, null, [], null, false)
        ToolCommand.of(['test', ' jacocoTestReport '], ['--info']) ==
                new ToolCommand(['test', 'jacocoTestReport'], ['--info'], null, null, [], null, false)
        ToolCommand.of(null, null) == ToolCommand.NONE
    }

    def "a command is empty only when it sets nothing: #description"() {
        expect:
        command.isEmpty() == empty

        where:
        description              | command                                                     || empty
        'nothing at all'         | ToolCommand.NONE                                            || true
        'blank values only'      | new ToolCommand([' '], [''], ' ', ' ', [' '], null, false)               || true
        'tasks'                  | ToolCommand.of(['build'], [])                               || false
        'flags'                  | ToolCommand.of([], ['--offline'])                           || false
        'a directory'            | new ToolCommand([], [], 'gui', null, [], null, false)                    || false
        'a Maven installation'   | new ToolCommand([], [], null, '/opt/maven', [], null, false)             || false
        'an environment'         | new ToolCommand([], [], null, null, ['CI=true'], null, false)            || false
        'a step label'           | new ToolCommand([], [], null, null, [], 'Build', false)                  || false
        'the captured output'    | new ToolCommand([], [], null, null, [], null, true)                      || false
    }

    def "a #tool command is written as #config"() {
        expect:
        written { command.writeTo(it, path, tool) } == config

        where:
        command                                                                | path              | tool    || config
        FULL                                                                   | 'build'           | GRADLE  || [build: [gradle: [tasks: ['clean', 'build'], flags: ['--no-daemon', '-x', 'test'], dir: 'gui', env: [JAVA_OPTS: '-Xmx2g', CI: 'true']]]]
        FULL                                                                   | 'tools.sonar'     | MAVEN   || [tools: [sonar: [maven: [goals: ['clean', 'build'], flags: ['--no-daemon', '-x', 'test'], dir: 'gui', mvnPath: '/opt/maven-3.9', env: [JAVA_OPTS: '-Xmx2g', CI: 'true']]]]]
        FULL                                                                   | 'build'           | FLUTTER || [:]
        ToolCommand.NONE                                                       | 'build'           | GRADLE  || [:]
        ToolCommand.NONE                                                       | 'build'           | MAVEN   || [:]
        new ToolCommand(['test'], [], null, '/opt/maven', ['NO_VALUE'], null, false)        | 'tests.unitTests' | MAVEN   || [tests: [unitTests: [maven: [goals: ['test'], mvnPath: '/opt/maven']]]]
        new ToolCommand(['build'], [], null, null, ['JUST_A_NAME'], null, false)            | 'build'           | GRADLE  || [build: [gradle: [tasks: ['build']]]]
        new ToolCommand(['build'], [], null, null, [], ' Build ', true)            | 'build'           | GRADLE  || [build: [gradle: [tasks: ['build'], label: 'Build', returnStdout: true]]]
        new ToolCommand(['deploy'], [], null, null, [], null, null)                | 'delivery'        | MAVEN   || [delivery: [maven: [goals: ['deploy']]]]
    }

    def "the environment becomes the env map, a value keeping any further '=' it contains"() {
        when:
        def command = new ToolCommand([], [], null, null,
                ['JAVA_OPTS=-Dfile.encoding=UTF-8 -Dx=y', 'EMPTY=', 'NO_SEPARATOR', '=no-name', 'NAME = spaced ', 'CI=true',
                 'CI=false'], null, false)

        then:
        command.environmentMap() == [JAVA_OPTS: '-Dfile.encoding=UTF-8 -Dx=y', EMPTY: '', NAME: 'spaced', CI: 'false']
        command.environmentMap().keySet() as List == ['JAVA_OPTS', 'EMPTY', 'NAME', 'CI']
        ToolCommand.NONE.environmentMap() == [:]
    }
}
