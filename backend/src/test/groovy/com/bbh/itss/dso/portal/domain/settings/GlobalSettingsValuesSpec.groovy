package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.settings.PlatformSettingsSpec.copy
import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA

class GlobalSettingsValuesSpec extends Specification {

    def bbh = GlobalSettingsValues.bbhDefaults()

    def "the BBH defaults hold zero limits for every scanner and the DSOEnhanced policy"() {
        expect:
        bbh.limits().keySet() as List == [SAST, SCA, NEXUS_IQ, DAST]
        bbh.limits().values().every { it == SeverityLimits.ZERO }
        bbh.platform().jenkinsUrl() == null
        bbh.platform().jenkinsLibrary() == 'DevSecOpsJenkinsLibrary'
        bbh.deployment() == new DeploymentDefaults('deploy.bbh.com', 'tomcat-app-process', 'rdltaapps1.testbbh.com',
                'qcltaapps1.testbbh.com', 'taadmin', 'scripts/deployment/zero-downtime-deployment.sh',
                'scripts/deployment/version.properties')
        bbh.scans() == new ScanSettings(60, 120, 50, 30, true, 40, 30, 60, 60, 30, 30, true, 5)
        bbh.releaseGate() == new ReleaseGateSettings([SAST, SCA, NEXUS_IQ, DAST], true, 'release-gate.json')
        bbh.serviceDefaults() == new ServiceDefaults(BuildTool.GRADLE, DeployTarget.VM, '.', 20)
        with(bbh.goldenFix()) {
            enabled()
            onlyDirectDependencies()
            minThreatLevel() == 2
            ecosystems() == ['maven', 'npm', 'pypi']
            goldenVersionTypes() == ['recommended-non-breaking-with-dependencies', 'recommended-non-breaking']
            excludeDirs() == []
            verifyEnabled()
            verifyMaxAttempts() == 3
            verifyTimeoutMinutes() == 20
            commitAuthorName() == 'DevSecOps GoldenFix'
            commitAuthorEmail() == 'devsecops-goldenfix@noreply.local'
            timeZone() == null
        }
    }

    def "limits are kept in scanner order without missing entries and cannot be changed"() {
        given:
        def limits = new LinkedHashMap<Scanner, SeverityLimits>()
        limits.put(DAST, new SeverityLimits(1, 2, 3))
        limits.put(null, SeverityLimits.ZERO)
        limits.put(SCA, null)
        limits.put(SAST, new SeverityLimits(0, 5, 10))

        when:
        def values = new GlobalSettingsValues(bbh.platform(), bbh.deployment(), limits, bbh.scans(), bbh.releaseGate(),
                bbh.serviceDefaults(), bbh.goldenFix())

        then:
        values.limits().keySet() as List == [SAST, DAST]
        values.limits() == [(SAST): new SeverityLimits(0, 5, 10), (DAST): new SeverityLimits(1, 2, 3)]

        when:
        values.limits().put(SCA, SeverityLimits.ZERO)

        then:
        thrown(UnsupportedOperationException)
    }

    def "no limits at all become an empty map"() {
        expect:
        new GlobalSettingsValues(bbh.platform(), bbh.deployment(), null, bbh.scans(), bbh.releaseGate(),
                bbh.serviceDefaults(), bbh.goldenFix()).limits() == [:]
    }

    def "changing the platform keeps every other value"() {
        given:
        def platform = bbh.platform().withJenkinsUrl('https://jenkins.bbh.com')

        when:
        def changed = bbh.withPlatform(platform)

        then:
        changed.platform().is(platform)
        changed == new GlobalSettingsValues(platform, bbh.deployment(), bbh.limits(), bbh.scans(), bbh.releaseGate(),
                bbh.serviceDefaults(), bbh.goldenFix())
    }

    def "the BBH defaults are valid"() {
        given:
        def problems = new ValidationProblems()

        when:
        bbh.validate(problems)

        then:
        problems.isEmpty()
    }

    def "validation reports the proxy pair, every scanner without limits and an incomplete GoldenFix policy"() {
        given:
        def values = new GlobalSettingsValues(copy(bbh.platform(), proxyPort: null), bbh.deployment(),
                [(SAST): SeverityLimits.ZERO, (SCA): SeverityLimits.ZERO], bbh.scans(), bbh.releaseGate(),
                bbh.serviceDefaults(), GoldenFixPolicy.inherit(true))
        def problems = new ValidationProblems()

        when:
        values.validate(problems)

        then:
        problems.list() == [
                new FieldProblem('platform.proxyPort', 'is required with a proxy host'),
                new FieldProblem('limits.NEXUS_IQ', 'set the limits of every scanner'),
                new FieldProblem('limits.DAST', 'set the limits of every scanner'),
                new FieldProblem('goldenFix.onlyDirectDependencies', 'is required in the global settings'),
                new FieldProblem('goldenFix.minThreatLevel', 'is required in the global settings'),
                new FieldProblem('goldenFix.verifyEnabled', 'is required in the global settings'),
                new FieldProblem('goldenFix.verifyMaxAttempts', 'is required in the global settings'),
                new FieldProblem('goldenFix.verifyTimeoutMinutes', 'is required in the global settings'),
                new FieldProblem('goldenFix.commitAuthorName', 'is required in the global settings'),
                new FieldProblem('goldenFix.commitAuthorEmail', 'is required in the global settings'),
                new FieldProblem('goldenFix.ecosystems', 'select at least one ecosystem'),
                new FieldProblem('goldenFix.goldenVersionTypes', 'add at least one remediation type')]
    }

    def "the library defaults are rendered in the shape and key order of its defaults.yaml"() {
        when:
        def defaults = bbh.defaultsConfig()

        then:
        defaults.keySet() as List == ['buildTool', 'deployTarget', 'sourceDir', 'coverage', 'tools', 'sast', 'sca',
                                      'dast', 'tests', 'releaseGate', 'goldenFix']
        defaults.buildTool == 'gradle'
        defaults.deployTarget == 'vm'
        defaults.sourceDir == '.'
        defaults.coverage == [minLine: 60]
        defaults.tools == [sonar  : [qualityGate: [waitForQualityGate: true, timeoutMinutes: 5]],
                           nexusIq: [maxCritical: 0, maxHigh: 0, maxMedium: 0]]
        (defaults.sast as Map).keySet() as List == ['prepareTimeoutMin', 'pollTimeoutMin', 'pollIntervalSec',
                                                    'maxCritical', 'maxHigh', 'maxMedium']
        defaults.sast == [prepareTimeoutMin: 120, pollTimeoutMin: 50, pollIntervalSec: 30, maxCritical: 0, maxHigh: 0,
                          maxMedium: 0]
        defaults.sca == [enabled: true, pollTimeoutMin: 40, pollIntervalSec: 30, maxCritical: 0, maxHigh: 0, maxMedium: 0]
        defaults.dast == [pollTimeoutMin: 60, pollIntervalSec: 60, reportTimeoutMin: 30, reportIntervalSec: 30,
                          maxCritical: 0, maxHigh: 0, maxMedium: 0]
        defaults.tests == [maxParallel: 20]
        defaults.releaseGate == [scanners: ['sast', 'sca', 'niq', 'dast'], requireCoverage: true,
                                 stateFile: 'release-gate.json']
        defaults.goldenFix == [enabled                : true,
                               onlyDirectDependencies : true,
                               minThreatLevel         : 2,
                               ecosystems             : ['maven', 'npm', 'pypi'],
                               goldenVersionTypes     : ['recommended-non-breaking-with-dependencies',
                                                         'recommended-non-breaking'],
                               verify                 : [enabled: true, maxAttempts: 3, timeoutMinutes: 20],
                               commitAuthorName       : 'DevSecOps GoldenFix',
                               commitAuthorEmail      : 'devsecops-goldenfix@noreply.local']
    }

    def "the global GoldenFix policy runs GoldenFix by default when it does not say otherwise"() {
        expect:
        new GlobalSettingsValues(bbh.platform(), bbh.deployment(), bbh.limits(), bbh.scans(), bbh.releaseGate(),
                bbh.serviceDefaults(), GoldenFixPolicy.INHERITED).goldenFix().enabled()
        !new GlobalSettingsValues(bbh.platform(), bbh.deployment(), bbh.limits(), bbh.scans(), bbh.releaseGate(),
                bbh.serviceDefaults(), GoldenFixPolicy.inherit(false)).goldenFix().enabled()
    }

    def "each scanner's limits are written to its own section of the defaults"() {
        given:
        def values = new GlobalSettingsValues(bbh.platform(), bbh.deployment(),
                [(NEXUS_IQ): new SeverityLimits(1, 4, 9), (DAST): new SeverityLimits(0, 2, 20)], bbh.scans(),
                bbh.releaseGate(), bbh.serviceDefaults(), bbh.goldenFix())

        when:
        def defaults = values.defaultsConfig()

        then:
        defaults.tools.nexusIq == [maxCritical: 1, maxHigh: 4, maxMedium: 9]
        defaults.dast.subMap(['maxCritical', 'maxHigh', 'maxMedium']) == [maxCritical: 0, maxHigh: 2, maxMedium: 20]
        !defaults.sast.containsKey('maxCritical')
        !defaults.sca.containsKey('maxCritical')
    }
}
