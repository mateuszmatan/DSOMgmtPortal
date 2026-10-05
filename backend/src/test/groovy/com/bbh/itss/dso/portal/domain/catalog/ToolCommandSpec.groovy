package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN

class ToolCommandSpec extends Specification {

    static final ToolCommand FULL = new ToolCommand(['clean', 'build'], ['--no-daemon', '-x', 'test'], 'gui',
            '/opt/maven-3.9', ['JAVA_OPTS=-Xmx2g', 'CI=true'])

    def "a command trims its values and keeps repeated tokens in their order"() {
        when:
        def command = new ToolCommand([' clean ', '', null, 'build', 'clean'], [' -s ', 'settings.xml', ' -gs ',
                'settings.xml', ' '], ' gui ', ' ', [' CI=true ', ' '])

        then:
        command.tasks() == ['clean', 'build', 'clean']
        command.flags() == ['-s', 'settings.xml', '-gs', 'settings.xml']
        command.directory() == 'gui'
        command.mavenHome() == null
        command.environment() == ['CI=true']
    }

    def "a command left out entirely is the empty command"() {
        expect:
        new ToolCommand(null, null, null, null, null) == ToolCommand.NONE
        ToolCommand.NONE.tasks() == []
        ToolCommand.NONE.flags() == []
        ToolCommand.NONE.environment() == []
    }

    def "a command of tasks and flags only has no directory, Maven installation or environment"() {
        when:
        def command = ToolCommand.of(['test', ' jacocoTestReport '], ['--info'])

        then:
        command == new ToolCommand(['test', 'jacocoTestReport'], ['--info'], null, null, [])
        ToolCommand.of(null, null) == ToolCommand.NONE
    }

    def "a command is empty only when it sets nothing: #description"() {
        expect:
        command.isEmpty() == empty

        where:
        description              | command                                                     || empty
        'nothing at all'         | ToolCommand.NONE                                            || true
        'blank values only'      | new ToolCommand([' '], [''], ' ', ' ', [' '])               || true
        'tasks'                  | ToolCommand.of(['build'], [])                               || false
        'flags'                  | ToolCommand.of([], ['--offline'])                           || false
        'a directory'            | new ToolCommand([], [], 'gui', null, [])                    || false
        'a Maven installation'   | new ToolCommand([], [], null, '/opt/maven', [])             || false
        'an environment'         | new ToolCommand([], [], null, null, ['CI=true'])            || false
    }

    def "a Gradle command is written under <path>.gradle without the Maven installation"() {
        given:
        def tree = new ConfigTree()

        when:
        FULL.writeTo(tree, 'build', GRADLE)

        then:
        tree.toMap() == [build: [gradle: [tasks: ['clean', 'build'], flags: ['--no-daemon', '-x', 'test'], dir: 'gui',
                                          env  : [JAVA_OPTS: '-Xmx2g', CI: 'true']]]]
    }

    def "a Maven command is written under <path>.maven with its goals and Maven installation"() {
        given:
        def tree = new ConfigTree()

        when:
        FULL.writeTo(tree, 'tools.sonar', MAVEN)

        then:
        tree.toMap() == [tools: [sonar: [maven: [goals  : ['clean', 'build'], flags: ['--no-daemon', '-x', 'test'],
                                                 dir    : 'gui', mvnPath: '/opt/maven-3.9',
                                                 env    : [JAVA_OPTS: '-Xmx2g', CI: 'true']]]]]
    }

    def "a Flutter build runs no Gradle or Maven command, so nothing is written"() {
        given:
        def tree = new ConfigTree()

        when:
        FULL.writeTo(tree, 'build', FLUTTER)

        then:
        tree.toMap() == [:]
    }

    def "an empty command writes nothing for any build tool"() {
        given:
        def tree = new ConfigTree()

        when:
        ToolCommand.NONE.writeTo(tree, 'build', tool)

        then:
        tree.toMap() == [:]

        where:
        tool << BuildTool.values()
    }

    def "only the parts that are set are written"() {
        given:
        def tree = new ConfigTree()

        when:
        new ToolCommand(['test'], [], null, '/opt/maven', ['NO_VALUE']).writeTo(tree, 'tests.unitTests', MAVEN)

        then:
        tree.toMap() == [tests: [unitTests: [maven: [goals: ['test'], mvnPath: '/opt/maven']]]]
    }

    def "the environment becomes the env map, a value keeping any further '=' it contains"() {
        when:
        def command = new ToolCommand([], [], null, null,
                ['JAVA_OPTS=-Dfile.encoding=UTF-8 -Dx=y', 'EMPTY=', 'NO_SEPARATOR', '=no-name', 'NAME = spaced ', 'CI=true',
                 'CI=false'])

        then:
        command.environmentMap() == [JAVA_OPTS: '-Dfile.encoding=UTF-8 -Dx=y', EMPTY: '', NAME: 'spaced', CI: 'false']
        command.environmentMap().keySet() as List == ['JAVA_OPTS', 'EMPTY', 'NAME', 'CI']
        ToolCommand.NONE.environmentMap() == [:]
    }

    def "an environment of entries without '=' writes no env map"() {
        given:
        def tree = new ConfigTree()

        when:
        new ToolCommand(['build'], [], null, null, ['JUST_A_NAME']).writeTo(tree, 'build', GRADLE)

        then:
        tree.toMap() == [build: [gradle: [tasks: ['build']]]]
    }
}
