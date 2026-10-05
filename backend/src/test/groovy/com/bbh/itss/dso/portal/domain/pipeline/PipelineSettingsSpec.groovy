package com.bbh.itss.dso.portal.domain.pipeline

import spock.lang.Specification

class PipelineSettingsSpec extends Specification {

    static final String JENKINS = 'https://jenkins.test'

    def both = new PipelineSettings(['linux'], 'CERT/gui-extended', 'CERT/gui-security', 'DevSecOps/CERT/gui', 'Nightly')

    def "blank settings are stored as null, agent labels cleaned, and a new service starts on the default agent"() {
        expect:
        PipelineSettings.forNewService() == new PipelineSettings(['linux-agent'], null, null, null, null)
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

    def "a pipeline's own job is linked under the Jenkins URL"() {
        expect:
        both.jenkinsJobUrl(JENKINS) == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui/'
        both.jenkinsJobUrl(null) == null
        new PipelineSettings(['linux'], null, null, null, null).jenkinsJobUrl(JENKINS) == null
        new PipelineSettings(['linux'], null, null, 'https://jenkins.bbh.com/job/x/', null).jenkinsJobUrl(null) ==
                'https://jenkins.bbh.com/job/x/'
    }
}
