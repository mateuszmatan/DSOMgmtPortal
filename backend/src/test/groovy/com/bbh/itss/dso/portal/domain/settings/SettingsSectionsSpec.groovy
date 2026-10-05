package com.bbh.itss.dso.portal.domain.settings

import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.settings.Scanner.DAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SCA

class SettingsSectionsSpec extends Specification {

    def deployment = GlobalSettingsValues.bbhDefaults().deployment()

    def "the #scanner scanner is #gateKey in the release gate and keeps its limits under #path"() {
        expect:
        scanner.gateKey() == gateKey
        scanner.defaultsPath() == path

        where:
        scanner  || gateKey | path
        SAST     || 'sast'  | 'sast'
        SCA      || 'sca'   | 'sca'
        NEXUS_IQ || 'niq'   | 'tools.nexusIq'
        DAST     || 'dast'  | 'dast'
    }

    def "severity limits are written under the given section"() {
        given:
        def tree = new ConfigTree()

        when:
        new SeverityLimits(1, 2, 3).writeTo(tree, 'tools.nexusIq')

        then:
        tree.toMap() == [tools: [nexusIq: [maxCritical: 1, maxHigh: 2, maxMedium: 3]]]
        SeverityLimits.ZERO == new SeverityLimits(0, 0, 0)
    }

    def "service defaults use the source root #expected for #sourceDir"() {
        expect:
        new ServiceDefaults(BuildTool.MAVEN, DeployTarget.OPENSHIFT, sourceDir, 4).sourceDir() == expected

        where:
        sourceDir || expected
        null      || '.'
        '   '     || '.'
        ' app '   || 'app'
    }

    def "service defaults are written as the library's values"() {
        given:
        def tree = new ConfigTree()

        when:
        new ServiceDefaults(BuildTool.MAVEN, DeployTarget.OPENSHIFT, 'src', 4).writeTo(tree)

        then:
        tree.toMap() == [buildTool: 'maven', deployTarget: 'openshift', sourceDir: 'src', tests: [maxParallel: 4]]
    }

    def "scan settings are written under coverage, the Sonar quality gate and each scanner"() {
        given:
        def tree = new ConfigTree()

        when:
        new ScanSettings(80, 10, 20, 15, false, 25, 35, 45, 55, 65, 75, false, 9).writeTo(tree)

        then:
        tree.toMap() == [coverage: [minLine: 80],
                         tools   : [sonar: [qualityGate: [waitForQualityGate: false, timeoutMinutes: 9]]],
                         sast    : [prepareTimeoutMin: 10, pollTimeoutMin: 20, pollIntervalSec: 15],
                         sca     : [enabled: false, pollTimeoutMin: 25, pollIntervalSec: 35],
                         dast    : [pollTimeoutMin: 45, pollIntervalSec: 55, reportTimeoutMin: 65, reportIntervalSec: 75]]
    }

    def "the release gate keeps each scanner once in scanner order and trims its state file"() {
        when:
        def gate = new ReleaseGateSettings([DAST, SAST, DAST, NEXUS_IQ], false, '  gate.json  ')

        then:
        gate.scanners() == [SAST, NEXUS_IQ, DAST]
        gate.stateFile() == 'gate.json'
        new ReleaseGateSettings(null, true, '  ').with { [scanners(), stateFile()] } == [[], null]
    }

    def "the release gate is written with the scanners' gate keys"() {
        given:
        def tree = new ConfigTree()

        when:
        new ReleaseGateSettings([NEXUS_IQ, SAST], false, 'gate.json').writeTo(tree)

        then:
        tree.toMap() == [releaseGate: [scanners: ['sast', 'niq'], requireCoverage: false, stateFile: 'gate.json']]
    }

    def "a release gate without scanners writes no scanner list"() {
        given:
        def tree = new ConfigTree()

        when:
        new ReleaseGateSettings([], true, 'release-gate.json').writeTo(tree)

        then:
        tree.toMap() == [releaseGate: [requireCoverage: true, stateFile: 'release-gate.json']]
    }

    def "deployment defaults add nothing to a service deployed to OpenShift"() {
        given:
        def tree = new ConfigTree().set('deployTarget', 'openshift')

        when:
        deployment.fillIn(tree, DeployTarget.OPENSHIFT)

        then:
        tree.toMap() == [deployTarget: 'openshift']
    }

    def "a VM service without SSH values of its own deploys to the global RD and QC hosts"() {
        given:
        def tree = new ConfigTree().set('deployTarget', 'vm')

        when:
        deployment.fillIn(tree, DeployTarget.VM)

        then:
        tree.toMap() == [deployTarget: 'vm',
                         deploy      : [vm: [rd: [host        : 'rdltaapps1.testbbh.com', user: 'taadmin',
                                                  deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                                                  versionFile : 'scripts/deployment/version.properties'],
                                             qc: [host        : 'qcltaapps1.testbbh.com', user: 'taadmin',
                                                  deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                                                  versionFile : 'scripts/deployment/version.properties']]]]
    }

    def "a changed global host reaches every VM service that does not set its own"() {
        given:
        def moved = new DeploymentDefaults('BBH', 'process', 'rdnew.testbbh.com', 'qcnew.testbbh.com', 'dsoadm',
                null, null)
        def tree = new ConfigTree().set('deploy.vm.qc.host', 'own-qc.testbbh.com')

        when:
        moved.fillIn(tree, DeployTarget.VM)

        then:
        tree.get('deploy.vm') == [qc: [host: 'own-qc.testbbh.com', user: 'dsoadm'],
                                  rd: [host: 'rdnew.testbbh.com', user: 'dsoadm']]
    }

    def "a VM region gets no section when neither the service nor the global settings have a value"() {
        given:
        def tree = new ConfigTree()

        when:
        new DeploymentDefaults(null, null, null, null, null, null, null).fillIn(tree, DeployTarget.VM)

        then:
        tree.toMap() == [:]
    }

    def "deployment defaults complete the sections a service configured and keep its own values"() {
        given:
        def tree = new ConfigTree()
                .set('deploy.vm.dod.siteName', 'own-site')
                .set('deploy.vm.rd.user', 'batchadm')
                .set('deploy.vm.rd.deployDir', '/opt/batch')

        when:
        deployment.fillIn(tree, DeployTarget.VM)

        then:
        tree.get('deploy.vm.dod') == [siteName: 'own-site', deployProcess: 'tomcat-app-process']
        tree.get('deploy.vm.rd') == [user        : 'batchadm', deployDir: '/opt/batch',
                                     host        : 'rdltaapps1.testbbh.com',
                                     deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                                     versionFile : 'scripts/deployment/version.properties']
        tree.get('deploy.vm.rd').keySet() as List == ['user', 'deployDir', 'host', 'deployScript', 'versionFile']
    }

    def "the QC section gets the QC host and no UrbanCode section is made up"() {
        given:
        def tree = new ConfigTree().set('deploy.vm.qc.deployDir', '/opt/app')

        when:
        deployment.fillIn(tree, DeployTarget.VM)

        then:
        tree.get('deploy.vm.qc') == [deployDir   : '/opt/app', host: 'qcltaapps1.testbbh.com', user: 'taadmin',
                                     deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
                                     versionFile : 'scripts/deployment/version.properties']
        tree.get('deploy.vm.rd.host') == 'rdltaapps1.testbbh.com'
        tree.get('deploy.vm.dod') == null
    }

    def "deployment defaults are trimmed and blank ones are null"() {
        expect:
        new DeploymentDefaults(' site ', ' process ', ' rd.bbh.com ', ' qc.bbh.com ', ' user ', ' ', null) ==
                new DeploymentDefaults('site', 'process', 'rd.bbh.com', 'qc.bbh.com', 'user', null, null)
    }
}
