package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

class GoldenFixPolicySpec extends Specification {

    static final GoldenFixPolicy COMPLETE = new GoldenFixPolicy(true, false, 7, ['maven', 'npm'],
            ['recommended-non-breaking', 'next-no-violations'], ['docs', 'examples'], true, 3, 30, 'mvn -B verify',
            './gradlew build', 'npm test', 'pytest', 'flutter test', 'GoldenFix', 'goldenfix@bbh.com', 'Europe/Warsaw')

    def "an inherited policy leaves every value, also whether GoldenFix runs, to the global settings"() {
        expect:
        GoldenFixPolicy.INHERITED == GoldenFixPolicy.inherit(null)
        with(GoldenFixPolicy.INHERITED) {
            enabled() == null
            onlyDirectDependencies() == null
            minThreatLevel() == null
            ecosystems() == []
            goldenVersionTypes() == []
            excludeDirs() == []
            verifyEnabled() == null
            verifyMaxAttempts() == null
            verifyTimeoutMinutes() == null
            verifyMavenCommand() == null
            verifyGradleCommand() == null
            verifyNpmCommand() == null
            verifyPipCommand() == null
            verifyPubCommand() == null
            commitAuthorName() == null
            commitAuthorEmail() == null
            timeZone() == null
        }
        !GoldenFixPolicy.inherit(false).enabled()
        GoldenFixPolicy.inherit(true).enabled()
    }

    def "a service that does not say whether GoldenFix runs follows the global default, and blank values are unset"() {
        when:
        def policy = new GoldenFixPolicy(null, null, null, [' maven ', 'maven', ' '], null, [' docs ', ''], null, null, null,
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ')

        then:
        policy.enabled() == null
        policy.ecosystems() == ['maven']
        policy.goldenVersionTypes() == []
        policy.excludeDirs() == ['docs']
        policy.verifyMavenCommand() == null
        policy.verifyGradleCommand() == null
        policy.verifyNpmCommand() == null
        policy.verifyPipCommand() == null
        policy.verifyPubCommand() == null
        policy.commitAuthorName() == null
        policy.commitAuthorEmail() == null
        policy.timeZone() == null
        new GoldenFixPolicy(false, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null) == GoldenFixPolicy.inherit(false)
    }

    def "a policy that inherits everything writes nothing, one that switches GoldenFix writes only that"() {
        expect:
        written(GoldenFixPolicy.INHERITED) == [:]
        written(GoldenFixPolicy.inherit(false)) == [goldenFix: [enabled: false]]
        written(GoldenFixPolicy.inherit(true)) == [goldenFix: [enabled: true]]
    }

    def "the global policy runs GoldenFix unless it is switched off"() {
        expect:
        GoldenFixPolicy.INHERITED.enabledByDefault() == GoldenFixPolicy.inherit(true)
        GoldenFixPolicy.inherit(false).enabledByDefault() == GoldenFixPolicy.inherit(false)
        COMPLETE.enabledByDefault().is(COMPLETE)
    }

    def "every value that is set is written under goldenFix"() {
        when:
        def goldenFix = written(COMPLETE).goldenFix

        then:
        goldenFix == [enabled           : true, onlyDirectDependencies: false, minThreatLevel: 7,
                      ecosystems        : ['maven', 'npm'], goldenVersionTypes: ['recommended-non-breaking', 'next-no-violations'],
                      excludeDirs       : ['docs', 'examples'],
                      verify            : [enabled : true, maxAttempts: 3, timeoutMinutes: 30,
                                           commands: [maven: 'mvn -B verify', gradle: './gradlew build', npm: 'npm test',
                                                      pip  : 'pytest', pub: 'flutter test']],
                      commitAuthorName  : 'GoldenFix', commitAuthorEmail: 'goldenfix@bbh.com', timeZone: 'Europe/Warsaw']
        goldenFix.keySet() as List == ['enabled', 'onlyDirectDependencies', 'minThreatLevel', 'ecosystems',
                                       'goldenVersionTypes', 'excludeDirs', 'verify', 'commitAuthorName', 'commitAuthorEmail',
                                       'timeZone']
    }

    def "a service that changes one verify command keeps the library's default for the others"() {
        given:
        def policy = new GoldenFixPolicy(true, null, null, [], [], [], null, null, null, null, ' ./gradlew check ', null,
                null, null, null, null, null)

        expect:
        written(policy) == [goldenFix: [enabled: true, verify: [commands: [gradle: './gradlew check']]]]
    }

    def "a complete policy satisfies the global settings"() {
        expect:
        problems(COMPLETE) == []
    }

    def "the global settings must set every value the library needs"() {
        given:
        def problems = new ValidationProblems()

        when:
        GoldenFixPolicy.INHERITED.validateComplete(problems.at('goldenFix'))

        then:
        problems.list()*.field == ['goldenFix.onlyDirectDependencies', 'goldenFix.minThreatLevel', 'goldenFix.verifyEnabled',
                                   'goldenFix.verifyMaxAttempts', 'goldenFix.verifyTimeoutMinutes',
                                   'goldenFix.commitAuthorName', 'goldenFix.commitAuthorEmail', 'goldenFix.ecosystems',
                                   'goldenFix.goldenVersionTypes']
        problems.list()*.message == ['is required in the global settings'] * 7 +
                ['select at least one ecosystem', 'add at least one remediation type']
    }

    def "verify commands, excluded folders and the time zone may stay unset in the global settings"() {
        given:
        def policy = new GoldenFixPolicy(true, true, 1, ['pub'], ['recommended-non-breaking'], [], false, 1, 1, null, null,
                null, null, null, 'GoldenFix', 'goldenfix@bbh.com', null)

        expect:
        problems(policy) == []
    }

    private static Map written(GoldenFixPolicy policy) {
        def tree = new ConfigTree()
        policy.writeTo(tree)
        tree.toMap()
    }

    private static List<String> problems(GoldenFixPolicy policy) {
        def problems = new ValidationProblems()
        policy.validateComplete(problems)
        problems.list()*.field
    }
}
