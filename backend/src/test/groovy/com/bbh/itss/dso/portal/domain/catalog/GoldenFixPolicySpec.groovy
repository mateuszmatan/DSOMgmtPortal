package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.shared.Sections.reported
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class GoldenFixPolicySpec extends Specification {

    static final GoldenFixPolicy COMPLETE = new GoldenFixPolicy(true, false, 7, ['maven', 'npm'],
            ['recommended-non-breaking', 'next-no-violations'], ['docs', 'examples'], true, 3, 30, 'mvn -B verify',
            './gradlew build', 'npm test', 'pytest', 'flutter test', 'GoldenFix', 'goldenfix@bbh.com', 'Europe/Warsaw')

    def "an inherited policy leaves every value, also whether GoldenFix runs, to the global settings"() {
        expect:
        GoldenFixPolicy.INHERITED == GoldenFixPolicy.inherit(null)
        GoldenFixPolicy.INHERITED == new GoldenFixPolicy(*([null] * 3), [], [], [], *([null] * 11))
        new GoldenFixPolicy(null, null, null, [' maven ', 'maven', ' '], null, [' docs ', ''], null, null, null,
                *([' '] * 8)) == new GoldenFixPolicy(*([null] * 3), ['maven'], [], ['docs'], *([null] * 11))
        new GoldenFixPolicy(false, *([null] * 16)) == GoldenFixPolicy.inherit(false)
        GoldenFixPolicy.inherit(true).enabled()
    }

    def "the global policy runs GoldenFix unless it is switched off"() {
        expect:
        GoldenFixPolicy.INHERITED.enabledByDefault() == GoldenFixPolicy.inherit(true)
        GoldenFixPolicy.inherit(false).enabledByDefault() == GoldenFixPolicy.inherit(false)
        COMPLETE.enabledByDefault().is(COMPLETE)
    }

    def "every value that is set is written under goldenFix, and only those"() {
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
        written(GoldenFixPolicy.INHERITED) == [:]
        written(GoldenFixPolicy.inherit(false)) == [goldenFix: [enabled: false]]
        written(new GoldenFixPolicy(true, null, null, [], [], [], null, null, null, null, ' ./gradlew check ', null,
                null, null, null, null, null)) == [goldenFix: [enabled: true, verify: [commands: [gradle: './gradlew check']]]]
    }

    def "the global settings must set every value the library needs, but not the commands, folders and time zone"() {
        when:
        def problems = complete(GoldenFixPolicy.INHERITED)

        then:
        problems*.field == ['onlyDirectDependencies', 'minThreatLevel', 'verifyEnabled', 'verifyMaxAttempts',
                            'verifyTimeoutMinutes', 'commitAuthorName', 'commitAuthorEmail', 'ecosystems', 'goldenVersionTypes']
        problems*.message == ['is required in the global settings'] * 7 +
                ['select at least one ecosystem', 'add at least one remediation type']
        complete(COMPLETE) == []
        complete(new GoldenFixPolicy(true, true, 1, ['pub'], ['recommended-non-breaking'], [], false, 1, 1, null, null,
                null, null, null, 'GoldenFix', 'goldenfix@bbh.com', null)) == []
    }

    private static List complete(GoldenFixPolicy policy) {
        reported { policy.validateComplete(it) }
    }
}
