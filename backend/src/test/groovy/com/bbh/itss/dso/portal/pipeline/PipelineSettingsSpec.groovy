package com.bbh.itss.dso.portal.pipeline

import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service

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

    def "a pipeline JPA loads starts without service, settings or keys"() {
        when:
        def loaded = Pipeline.getDeclaredConstructor().with { accessible = true; newInstance() }
        def key = PipelineKey.getDeclaredConstructor().with { accessible = true; newInstance() }

        then:
        loaded.service == null
        loaded.settings == null
        loaded.keys == []
        !loaded.enabled
        key.pipeline == null
        key.value == null
    }

    def "a pipeline created with a job of the wrong type drops it"() {
        when:
        def created = pipeline(service(product(), name: 'gui'), type: PipelineType.EXTENDED,
                extendedPipelineJob: 'CERT/gui-extended', securityPipelineJob: 'CERT/gui-security')

        then:
        created.settings.extendedPipelineJob() == null
        created.settings.securityPipelineJob() == 'CERT/gui-security'
    }
}
