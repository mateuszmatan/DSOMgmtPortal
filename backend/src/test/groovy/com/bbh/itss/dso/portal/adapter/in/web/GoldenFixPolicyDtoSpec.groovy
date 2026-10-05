package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

class GoldenFixPolicyDtoSpec extends Specification {

    static final GoldenFixPolicy COMPLETE = new GoldenFixPolicy(true, false, 7, ['maven', 'npm'],
            ['recommended-non-breaking', 'next-no-violations'], ['docs', 'examples'], true, 3, 30, 'mvn -B verify',
            './gradlew build', 'npm test', 'pytest', 'flutter test', 'GoldenFix', 'goldenfix@bbh.com', 'Europe/Warsaw')

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "a policy travels through the API unchanged"() {
        expect:
        GoldenFixPolicyDto.from(COMPLETE).toDomain() == COMPLETE
        GoldenFixPolicyDto.from(GoldenFixPolicy.inherit(false)).toDomain() == GoldenFixPolicy.inherit(false)
    }

    def "a request keeps GoldenFix on unless it is switched off, and blank values are unset before validation"() {
        when:
        def dto = new GoldenFixPolicyDto(null, null, null, [' maven ', 'maven', ' '], null, [' docs ', ''], null, null,
                null, ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ')

        then:
        dto.enabled()
        dto.ecosystems() == ['maven']
        dto.goldenVersionTypes() == []
        dto.excludeDirs() == ['docs']
        [dto.verifyMavenCommand(), dto.verifyGradleCommand(), dto.verifyNpmCommand(), dto.verifyPipCommand(),
         dto.verifyPubCommand(), dto.commitAuthorName(), dto.commitAuthorEmail(), dto.timeZone()].every { it == null }
        validator.validate(dto).isEmpty()
    }

    def "bean validation accepts a complete policy"() {
        expect:
        validator.validate(GoldenFixPolicyDto.from(COMPLETE)).isEmpty()
        validator.validate(GoldenFixPolicyDto.from(GoldenFixPolicy.INHERITED)).isEmpty()
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(GoldenFixPolicyDto.from(policy))*.propertyPath*.toString() == [property]

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
}
