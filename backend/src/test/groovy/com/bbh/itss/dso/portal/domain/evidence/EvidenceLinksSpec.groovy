package com.bbh.itss.dso.portal.domain.evidence

import spock.lang.Specification

class EvidenceLinksSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String JOB = 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
    static final String BUILD = JOB + '42/'

    def "a run links its Jenkins build, the pages the build publishes and the scanners"() {
        when:
        def links = EvidenceLinks.of(BUILD, 'https://bbh.cloud.appscan.com', APP_ID, 'https://tools.bbh.com/sonar',
                'cert-gui', 'https://tools.bbh.com/IQ')

        then:
        links.buildUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/42/'
        links.reportUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/42/Pipeline_20Report/'
        links.testReportUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/42/testReport/'
        links.artifactsUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/42/artifact/'
        links.appScanUrl() == "https://bbh.cloud.appscan.com/main/myapps/$APP_ID/scans"
        links.sonarUrl() == 'https://tools.bbh.com/sonar/dashboard?id=cert-gui'
        links.nexusIqUrl() == 'https://tools.bbh.com/IQ/'
    }

    def "trailing slashes of the tool servers are dropped and the identifiers encoded"() {
        when:
        def links = EvidenceLinks.of(BUILD, ' https://eu.cloud.appscan.com// ', ' app/1 x ', 'https://sonar.bbh.com/',
                'cert gui&branch=main', ' https://iq.bbh.com/platform// ')

        then:
        links.appScanUrl() == 'https://eu.cloud.appscan.com/main/myapps/app%2F1%20x/scans'
        links.sonarUrl() == 'https://sonar.bbh.com/dashboard?id=cert%20gui%26branch%3Dmain'
        links.nexusIqUrl() == 'https://iq.bbh.com/platform/'
    }

    def "without a build there is nothing of a build to link"() {
        when:
        def links = EvidenceLinks.of(null, null, null, null, null, null)

        then:
        links.buildUrl() == null
        links.reportUrl() == null
        links.testReportUrl() == null
        links.artifactsUrl() == null
    }

    def "the pages a build publishes are linked under it"() {
        when:
        def links = EvidenceLinks.of(BUILD, null, null, null, null, null)

        then:
        links.buildUrl() == BUILD
        links.reportUrl() == BUILD + 'Pipeline_20Report/'
        links.testReportUrl() == BUILD + 'testReport/'
        links.artifactsUrl() == BUILD + 'artifact/'
    }

    def "a scanner is linked only when both its server and the service's identifier are known"() {
        when:
        def links = EvidenceLinks.of(null, asocUrl, appId, sonarUrl, sonarKey, nexusIqUrl)

        then:
        links.appScanUrl() == null
        links.sonarUrl() == null
        links.nexusIqUrl() == null

        where:
        asocUrl                         | appId  | sonarUrl                      | sonarKey   | nexusIqUrl
        null                            | APP_ID | null                          | 'cert-gui' | null
        'https://bbh.cloud.appscan.com' | null   | 'https://tools.bbh.com/sonar' | null       | ''
        ' '                             | ' '    | ' '                           | ' '        | '  '
    }

    def "the report the library publishes is the Pipeline Report page"() {
        expect:
        EvidenceLinks.PIPELINE_REPORT == 'Pipeline_20Report/'
    }
}
