package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.common.ValidationProblems
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

class GoldenFixPolicySpec extends Specification {

    /** The BBH defaults as the global settings hold them, every value set. */
    static final GoldenFixPolicy COMPLETE = new GoldenFixPolicy(true, false, 7, ['maven', 'npm'],
            ['recommended-non-breaking', 'next-no-violations'], ['docs', 'examples'], true, 3, 30, 'mvn -B verify',
            './gradlew build', 'npm test', 'pytest', 'flutter test', 'GoldenFix', 'goldenfix@bbh.com', 'Europe/Warsaw')

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "an inherited policy only says whether GoldenFix runs"() {
        expect:
        GoldenFixPolicy.INHERITED == GoldenFixPolicy.inherit(true)
        with(GoldenFixPolicy.INHERITED) {
            enabled()
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
    }

    def "GoldenFix stays on unless it is switched off, and blank values are unset"() {
        when:
        def policy = new GoldenFixPolicy(null, null, null, [' maven ', 'maven', ' '], null, [' docs ', ''], null, null, null,
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ')

        then:
        policy.enabled()
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

    def "an inherited policy writes only whether GoldenFix runs"() {
        expect:
        written(GoldenFixPolicy.INHERITED) == [goldenFix: [enabled: true]]
        written(GoldenFixPolicy.inherit(false)) == [goldenFix: [enabled: false]]
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

    def "bean validation accepts a complete policy"() {
        expect:
        validator.validate(COMPLETE).isEmpty()
        validator.validate(GoldenFixPolicy.INHERITED).isEmpty()
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(policy)*.propertyPath*.toString() == [property]

        where:
        description                      | policy                                        || property
        'a threat level of zero'         | change(minThreatLevel: 0)                     || 'minThreatLevel'
        'a threat level above ten'       | change(minThreatLevel: 11)                    || 'minThreatLevel'
        'an unknown ecosystem'           | change(ecosystems: ['maven', 'gradle'])       || 'ecosystems[1].<list element>'
        'a remediation type in capitals' | change(goldenVersionTypes: ['Recommended'])   || 'goldenVersionTypes[0].<list element>'
        'more than 10 remediation types' | change(goldenVersionTypes: ('a'..'k').toList()) || 'goldenVersionTypes'
        'a too long excluded folder'     | change(excludeDirs: ['d' * 201])              || 'excludeDirs[0].<list element>'
        'more than 30 excluded folders'  | change(excludeDirs: (1..31).collect { "d$it" as String }) || 'excludeDirs'
        'no verify attempt'              | change(verifyMaxAttempts: 0)                  || 'verifyMaxAttempts'
        'more than 10 verify attempts'   | change(verifyMaxAttempts: 11)                 || 'verifyMaxAttempts'
        'a verify timeout over 4 hours'  | change(verifyTimeoutMinutes: 241)             || 'verifyTimeoutMinutes'
        'a too long Maven command'       | change(verifyMavenCommand: 'm' * 501)         || 'verifyMavenCommand'
        'a too long pub command'         | change(verifyPubCommand: 'p' * 501)           || 'verifyPubCommand'
        'a too long author name'         | change(commitAuthorName: 'n' * 201)           || 'commitAuthorName'
        'an author email without @'      | change(commitAuthorEmail: 'goldenfix.bbh.com') || 'commitAuthorEmail'
        'a time zone with a space'       | change(timeZone: 'Europe Warsaw')             || 'timeZone'
    }

    /** The complete policy with the named values changed. */
    private static GoldenFixPolicy change(Map args) {
        Map values = [enabled             : COMPLETE.enabled(), onlyDirectDependencies: COMPLETE.onlyDirectDependencies(),
                      minThreatLevel      : COMPLETE.minThreatLevel(), ecosystems: COMPLETE.ecosystems(),
                      goldenVersionTypes  : COMPLETE.goldenVersionTypes(), excludeDirs: COMPLETE.excludeDirs(),
                      verifyEnabled       : COMPLETE.verifyEnabled(), verifyMaxAttempts: COMPLETE.verifyMaxAttempts(),
                      verifyTimeoutMinutes: COMPLETE.verifyTimeoutMinutes(), verifyMavenCommand: COMPLETE.verifyMavenCommand(),
                      verifyGradleCommand : COMPLETE.verifyGradleCommand(), verifyNpmCommand: COMPLETE.verifyNpmCommand(),
                      verifyPipCommand    : COMPLETE.verifyPipCommand(), verifyPubCommand: COMPLETE.verifyPubCommand(),
                      commitAuthorName    : COMPLETE.commitAuthorName(), commitAuthorEmail: COMPLETE.commitAuthorEmail(),
                      timeZone            : COMPLETE.timeZone()] + args
        new GoldenFixPolicy(values.enabled as Boolean, values.onlyDirectDependencies as Boolean,
                values.minThreatLevel as Integer, values.ecosystems as List<String>, values.goldenVersionTypes as List<String>,
                values.excludeDirs as List<String>, values.verifyEnabled as Boolean, values.verifyMaxAttempts as Integer,
                values.verifyTimeoutMinutes as Integer, values.verifyMavenCommand as String,
                values.verifyGradleCommand as String, values.verifyNpmCommand as String, values.verifyPipCommand as String,
                values.verifyPubCommand as String, values.commitAuthorName as String, values.commitAuthorEmail as String,
                values.timeZone as String)
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
