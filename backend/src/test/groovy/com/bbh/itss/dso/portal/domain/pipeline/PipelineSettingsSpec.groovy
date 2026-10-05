package com.bbh.itss.dso.portal.domain.pipeline

import spock.lang.Specification

class PipelineSettingsSpec extends Specification {

    static final String JENKINS = 'https://jenkins.test'

    def both = new PipelineSettings(['linux'], 'CERT/gui-extended', 'CERT/gui-security', 'DevSecOps/CERT/gui', 'Nightly')

    def "the #type pipeline keeps extended job #extended and security job #security"() {
        when:
        def settings = both.forType(type)

        then:
        settings.extendedPipelineJob() == extended
        settings.securityPipelineJob() == security
        settings.agentLabels() == ['linux']
        settings.jenkinsJob() == 'DevSecOps/CERT/gui'
        settings.description() == 'Nightly'

        where:
        type                  || extended            | security
        PipelineType.SECURITY || 'CERT/gui-extended' | null
        PipelineType.EXTENDED || null                | 'CERT/gui-security'
        PipelineType.FULL     || null                | null
        PipelineType.SAST     || null                | null
    }

    def "blank settings are stored as null and agent labels cleaned"() {
        expect:
        new PipelineSettings([' linux ', '', 'linux', 'docker'], ' ', '  ', ' DevSecOps/gui ', '\t') ==
                new PipelineSettings(['linux', 'docker'], null, null, 'DevSecOps/gui', null)
        new PipelineSettings(null, null, null, null, null).agentLabels() == []
    }

    def "the job #job under Jenkins #jenkinsUrl is linked as #url"() {
        expect:
        PipelineSettings.jobUrl(job, jenkinsUrl) == url

        where:
        job                                         | jenkinsUrl                || url
        null                                        | JENKINS                   || null
        '   '                                       | JENKINS                   || null
        'https://jenkins.bbh.com/job/CERT/job/gui/' | null                      || 'https://jenkins.bbh.com/job/CERT/job/gui/'
        ' http://jenkins.bbh.com/job/gui '          | JENKINS                   || 'http://jenkins.bbh.com/job/gui'
        'DevSecOps/TARA/app-full'                   | null                      || null
        'DevSecOps/TARA/app-full'                   | '  '                      || null
        'DevSecOps/TARA/app-full'                   | JENKINS                   || 'https://jenkins.test/job/DevSecOps/job/TARA/job/app-full/'
        'app-full'                                  | 'https://jenkins.test/ci' || 'https://jenkins.test/ci/job/app-full/'
        ' /DevSecOps//TARA/ '                       | 'https://jenkins.test///' || 'https://jenkins.test/job/DevSecOps/job/TARA/'
        'CERT Scanner/ gui#1 '                      | JENKINS                   || 'https://jenkins.test/job/CERT%20Scanner/job/gui%231/'
        'Zażółć/gęślą'                              | JENKINS                   || 'https://jenkins.test/job/Za%C5%BC%C3%B3%C5%82%C4%87/job/g%C4%99%C5%9Bl%C4%85/'
    }

    def "a path segment keeps the characters RFC 3986 allows in it and encodes the rest in upper case hex"() {
        expect:
        PipelineSettings.encodePathSegment(segment) == encoded

        where:
        segment                       || encoded
        'AZaz09-._~'                  || 'AZaz09-._~'
        "!\$&'()*+,;=:@"              || "!\$&'()*+,;=:@"
        'a b/c?d#e%f[g]h"i<j>k\\l^m`' || 'a%20b%2Fc%3Fd%23e%25f%5Bg%5Dh%22i%3Cj%3Ek%5Cl%5Em%60'
        '{|}'                         || '%7B%7C%7D'
        'é€'                          || '%C3%A9%E2%82%AC'
        '\u007f'                      || '%7F'
    }

    def "a pipeline's own job is linked under the Jenkins URL"() {
        expect:
        both.jenkinsJobUrl(JENKINS) == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui/'
        both.jenkinsJobUrl(null) == null
        new PipelineSettings(['linux'], null, null, null, null).jenkinsJobUrl(JENKINS) == null
        new PipelineSettings(['linux'], null, null, 'https://jenkins.bbh.com/job/x/', null).jenkinsJobUrl(null) ==
                'https://jenkins.bbh.com/job/x/'
    }
}
