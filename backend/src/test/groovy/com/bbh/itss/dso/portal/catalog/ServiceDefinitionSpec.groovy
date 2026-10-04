package com.bbh.itss.dso.portal.catalog

import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

import static com.bbh.itss.dso.portal.catalog.Region.QC
import static com.bbh.itss.dso.portal.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static com.bbh.itss.dso.portal.support.Fixtures.withId

class ServiceDefinitionSpec extends Specification {

    def product = product()

    def "a service gives back every section it was created with"() {
        given:
        def settings = fullSettings()

        when:
        def service = product.addService('gui', 'Angular front end', 0, settings)

        then:
        service.settings() == settings
        service.settings().sshTargets().keySet() as List == [RD, QC]
        service.settings().urbanCodeApplications()*.applicationName() == ['Cert', 'Cert Batch']
    }

    def "a service created without the optional sections gives back their defaults"() {
        when:
        def service = product.addService('gui', null, 0, ServiceSettings.of(build(), deployment(), appScan()))

        then:
        service.settings() == settings(metrics: new MetricsSettings(true, 'CERT-gui', 'test'))
    }

    def "saving unchanged lists keeps the stored rows"() {
        given:
        def service = product.addService('gui', null, 0, fullSettings())
        def jobs = service.settings().testJobs()
        def applications = new ArrayList(stored(service, 'urbanCodeApplications'))
        def sshTargets = new HashMap(stored(service, 'sshTargets'))
        def openShiftTargets = new HashMap(stored(service, 'openShiftTargets'))

        when:
        service.update('gui', 'changed', 1, fullSettings())

        then:
        service.description == 'changed'
        service.displayOrder == 1
        identical(service.settings().testJobs(), jobs)
        identical(stored(service, 'urbanCodeApplications'), applications)
        stored(service, 'sshTargets')[RD].is(sshTargets[RD])
        stored(service, 'sshTargets')[QC].is(sshTargets[QC])
        stored(service, 'openShiftTargets')[RD].is(openShiftTargets[RD])
    }

    def "changed lists are replaced as a whole"() {
        given:
        def service = product.addService('gui', null, 0, fullSettings())
        def oldApplications = new ArrayList(stored(service, 'urbanCodeApplications'))
        def job = new TestJob(TestStage.PERFORMANCE, null, null, 'CERT/load', null, null, null, null, null)
        def first = new UrbanCodeApplicationSettings('Cert', 1, [], null, [])
        def second = new UrbanCodeApplicationSettings('Cert Reports', 2, [], null, [])
        def qc = new SshTarget('qc2.host', null, null, null, null)

        when:
        service.update('gui', null, 0, settings(testJobs: [job], urbanCodeApplications: [first, second],
                sshTargets: [(QC): qc], openShiftTargets: [:], metrics: new MetricsSettings(true, 'cert', 'test')))

        then:
        service.settings().testJobs() == [job]
        service.settings().testJobs()[0].is(job)
        service.settings().urbanCodeApplications() == [first, second]
        List<UrbanCodeApplication> applications = stored(service, 'urbanCodeApplications')
        applications*.position == [0, 1]
        applications.every { ReflectionTestUtils.getField(it, 'service').is(service) }
        applications.every { application -> oldApplications.every { !it.is(application) } }
        service.settings().sshTargets() == [(QC): qc]
        service.settings().openShiftTargets() == [:]
    }

    def "removing every list entry leaves the lists empty"() {
        given:
        def service = product.addService('gui', null, 0, fullSettings())

        when:
        service.update('gui', null, 0, settings())

        then:
        with(service.settings()) {
            testJobs() == []
            urbanCodeApplications() == []
            sshTargets() == [:]
            openShiftTargets() == [:]
        }
    }

    def "updating a service replaces every single-value section"() {
        given:
        def service = product.addService('gui', null, 0, fullSettings())
        def changed = settings(build: build(tool: BuildTool.MAVEN), deployment: deployment(target: DeployTarget.OPENSHIFT,
                appName: 'cert', artifactName: 'cert.jar'))

        when:
        service.update(' web ', ' ', 3, changed)

        then:
        service.name == 'web'
        service.description == null
        service.displayOrder == 3
        service.settings() == settings(build: build(tool: BuildTool.MAVEN), deployment: deployment(
                target: DeployTarget.OPENSHIFT, appName: 'cert', artifactName: 'cert.jar'),
                metrics: new MetricsSettings(true, 'CERT-web', 'test'))
    }

    def "the getters return the stored sections"() {
        given:
        def settings = fullSettings()
        def service = withId(product.addService('gui', 'Angular', 2, settings), 15L)

        expect:
        service.id == 15L
        service.product.is(product)
        service.name == 'gui'
        service.description == 'Angular'
        service.displayOrder == 2
        service.build == settings.build()
        service.deployment == settings.deployment()
        service.appScan == settings.appScan()
        service.sonar == settings.sonar()
        service.nexusIq == settings.nexusIq()
        service.scm == settings.scm()
        service.metrics == settings.metrics()
    }

    def "sections JPA loads as null are read as their defaults"() {
        given:
        def service = product.addService('gui', null, 0, fullSettings())
        ['metrics', 'sonar', 'nexusIq', 'scm'].each { ReflectionTestUtils.setField(service, it, null) }

        expect:
        service.metrics == MetricsSettings.DEFAULTS
        service.sonar == SonarSettings.NONE
        service.nexusIq == NexusIqSettings.NONE
        service.scm == ScmSettings.NONE
    }

    def "a service loaded by JPA before its fields are set answers the defaults"() {
        when:
        def service = new ServiceDefinition()

        then:
        service.id == null
        service.product == null
        service.metrics == MetricsSettings.DEFAULTS
        service.sonar == SonarSettings.NONE
        service.nexusIq == NexusIqSettings.NONE
        service.scm == ScmSettings.NONE
        service.appScan == null
        service.build == null
        service.deployment == null
    }

    def "a service writes the sections that apply to it and the product's AppScan account"() {
        given:
        def service = product.addService('gui', null, 0, fullSettings())
        def tree = new ConfigTree()

        when:
        service.writeTo(tree)

        then:
        tree.get('deploy.vm.rd') == [host: 'rd.host', user: 'dsoadm']
        tree.get('deploy.vm.qc') == [host: 'qc.host']
        tree.get('deploy.vm.dod.applications')*.applicationName == ['Cert', 'Cert Batch']
        tree.get('deploy.openshift') == null
        tree.get('tests.smoke.jobs') == [[name: 'smoke', job: 'CERT/gui-smoke']]
        tree.get('goldenFix') == [enabled: false]
        tree.get('asoc') == [keyId: 'bbh_key-id', token: 'hcl-app-scan-account']
        tree.get('influx') == [enabled: true, project: 'cert-gui', env: 'qc']
    }

    /** Every section set, built anew on each call so equal settings are different instances. */
    private static ServiceSettings fullSettings() {
        def sshTargets = new LinkedHashMap<Region, SshTarget>()
        sshTargets[QC] = new SshTarget('qc.host', null, null, null, null)
        sshTargets[RD] = new SshTarget('rd.host', 'dsoadm', null, null, null)
        settings(
                unitTests: new UnitTestSettings(command(['test']), '**/TEST-*.xml', null, null, false, 'jacoco.xml'),
                tests: new TestSettings(4, 2, null, null),
                testJobs: [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', null, null, null, null, null),
                           new TestJob(TestStage.REGRESSION, null, TestJobType.REMOTE, 'https://jenkins-qc/job/r/', 60, null,
                                   null, null, null)],
                delivery: command(['publish']),
                urbanCode: new UrbanCodeSettings('BBH-RD', null, false, true, false, true, false, null, null),
                urbanCodeApplications: [
                        new UrbanCodeApplicationSettings('Cert', 1, ['RD'], null,
                                [new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', null, null, null, true)]),
                        new UrbanCodeApplicationSettings('Cert Batch', 2, [], 'batch', [])],
                sshTargets: sshTargets,
                openShiftTargets: [(RD): new OpenShiftTarget('cert-build', null, null, null, null, null, null, null, null,
                        'cert-rd', null, null, false, null, null, null, null, null, null)],
                sonar: SonarSettings.of('CertScanner GUI', 'cert-gui', command(['sonarqube'])),
                nexusIq: NexusIqSettings.of('cert', ['**/*.war']),
                scm: ScmSettings.of('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds'),
                goldenFix: GoldenFixPolicy.inherit(false),
                metrics: new MetricsSettings(true, 'cert-gui', 'qc'),
                flutter: new FlutterSettings(FlutterPlatform.APK, ['app'], [], [], [], null, null, null, null, null, null, null,
                        null, false, null, null))
    }

    private static <T> T stored(ServiceDefinition service, String field) {
        ReflectionTestUtils.getField(service, field) as T
    }

    private static boolean identical(List actual, List expected) {
        actual.size() == expected.size() && (0..<actual.size()).every { actual[it].is(expected[it]) }
    }
}
