package com.bbh.dso.portal.catalog

import com.bbh.dso.portal.common.ValidationProblems
import spock.lang.Specification

import static com.bbh.dso.portal.support.Fixtures.appScan
import static com.bbh.dso.portal.support.Fixtures.build
import static com.bbh.dso.portal.support.Fixtures.deployment

class ServiceSettingsSpec extends Specification {

    def "optional sections left out are empty"() {
        when:
        def settings = new ServiceSettings(build(), deployment(), appScan(), null, null, null, null, null)

        then:
        settings.sonar() == SonarSettings.NONE
        settings.nexusIq() == NexusIqSettings.NONE
        settings.scm() == ScmSettings.NONE
        settings.metrics() == MetricsSettings.DEFAULTS
        settings.additionalConfig() == AdditionalConfig.NONE
    }

    def "the additional YAML is written first so the dedicated fields win"() {
        given:
        def settings = new ServiceSettings(build(), deployment(), appScan(), null, null, null, null,
                new AdditionalConfig('buildTool: maven\ntests:\n  smoke:\n    enabled: true'))
        def tree = new ConfigTree()

        when:
        settings.sections().each { it.writeTo(tree) }

        then:
        settings.sections().first() == settings.additionalConfig()
        settings.sections().size() == 8
        tree.get('buildTool') == 'gradle'
        tree.get('tests.smoke.enabled') == true
    }

    def "each section reports its problems under its own name"() {
        given:
        def settings = new ServiceSettings(build(javaPath: null), deployment(target: DeployTarget.OPENSHIFT),
                appScan(dastEnabled: true), null, null, null, null, new AdditionalConfig('unknown: 1'))
        def problems = new ValidationProblems()

        when:
        settings.validate(problems.at('services[3]'))

        then:
        problems.list()*.field == ['services[3].build.javaPath', 'services[3].deployment.appName',
                                   'services[3].deployment.artifactName', 'services[3].appScan.dastTargetUrl',
                                   'services[3].additionalConfig.yaml']
    }
}
