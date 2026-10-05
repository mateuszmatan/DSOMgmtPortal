package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.DomainFixtures.appScan
import static com.bbh.itss.dso.portal.support.DomainFixtures.build
import static com.bbh.itss.dso.portal.support.DomainFixtures.command
import static com.bbh.itss.dso.portal.support.DomainFixtures.deployment
import static com.bbh.itss.dso.portal.support.DomainFixtures.settings

class ServiceSettingsSpec extends Specification {

    static final SshTarget RD_HOST = new SshTarget('rd.host', null, null, null, null)
    static final OpenShiftTarget RD_PROJECT = new OpenShiftTarget('cert-build', null, null, null, null, null, null,
            null, null, 'cert-rd', null, null, false, null, null, null, null, null, null)
    static final UrbanCodeApplicationSettings UCD_APP = new UrbanCodeApplicationSettings('Cert', 1, ['RD'], null,
            [new UrbanCodeComponent('cert-gui', 'build', '*.war', null, null, null, false)])

    def "sections left out take their defaults"() {
        when:
        def settings = ServiceSettings.of(build(), deployment(), appScan())

        then:
        settings.unitTests() == UnitTestSettings.NONE
        settings.tests() == TestSettings.DEFAULTS
        settings.testJobs() == []
        settings.delivery() == ToolCommand.NONE
        settings.urbanCode() == UrbanCodeSettings.DEFAULTS
        settings.urbanCodeApplications() == []
        settings.sshTargets() == [:]
        settings.openShiftTargets() == [:]
        settings.sonar() == SonarSettings.NONE
        settings.nexusIq() == NexusIqSettings.NONE
        settings.scm() == ScmSettings.NONE
        settings.goldenFix() == GoldenFixPolicy.INHERITED
        settings.metrics() == MetricsSettings.DEFAULTS
        settings.flutter() == FlutterSettings.NONE
    }

    def "deployment targets that set nothing, or have no region, are dropped and the rest kept in region order"() {
        given:
        def ssh = new LinkedHashMap<Region, SshTarget>()
        ssh[Region.QC] = new SshTarget('qc.host', null, null, null, null)
        ssh[Region.RD] = new SshTarget(' ', null, null, null, null)
        ssh[null] = RD_HOST

        when:
        def settings = settings(sshTargets: ssh, openShiftTargets: [(Region.RD): RD_PROJECT])

        then:
        settings.sshTargets().keySet() as List == [Region.QC]
        settings.openShiftTargets().keySet() as List == [Region.RD]
    }

    def "a Gradle service on a VM writes its UrbanCode and SSH deployment but no delivery or OpenShift section"() {
        given:
        def settings = settings(delivery: command(['publish']), urbanCodeApplications: [UCD_APP],
                sshTargets: [(Region.RD): RD_HOST], openShiftTargets: [(Region.RD): RD_PROJECT])
        def tree = new ConfigTree()

        when:
        settings.writeTo(tree)

        then:
        tree.get('build.gradle.tasks') == ['clean', 'build']
        tree.get('delivery') == null
        tree.get('deploy.vm.rd') == [host: 'rd.host']
        tree.get('deploy.vm.dod.applications')*.applicationName == ['Cert']
        tree.get('deploy.openshift') == null
        tree.get('flutter') == null
    }

    def "a Maven service on a VM also writes the goals that upload its snapshot"() {
        given:
        def tree = new ConfigTree()

        when:
        settings(build: build(tool: BuildTool.MAVEN), delivery: command(['deploy:deploy-file'], ['-DrepositoryId=bbh']))
                .writeTo(tree)

        then:
        tree.get('delivery') == [maven: [goals: ['deploy:deploy-file'], flags: ['-DrepositoryId=bbh']]]
    }

    def "a service on OpenShift writes its OpenShift projects and nothing of the VM deployment"() {
        given:
        def tree = new ConfigTree()

        when:
        settings(deployment: deployment(target: DeployTarget.OPENSHIFT, appName: 'cert', artifactName: 'cert.jar'),
                urbanCodeApplications: [UCD_APP], sshTargets: [(Region.RD): RD_HOST],
                openShiftTargets: [(Region.RD): RD_PROJECT]).writeTo(tree)

        then:
        tree.get('deploy.openshift.rd') == [projectBuildR: 'cert-build', projectDeploymentR: 'cert-rd']
        tree.get('deploy.vm') == null
    }

    def "only a Flutter service writes its Flutter section"() {
        given:
        def flutter = new FlutterSettings(FlutterPlatform.APK, ['app'], [], [], [], 'sign', 'prod-license',
                'test-license', null, null, null, null, null, false, null, null)
        def flutterTree = new ConfigTree()
        def gradleTree = new ConfigTree()

        when:
        settings(build: build(tool: BuildTool.FLUTTER, javaPath: null), flutter: flutter).writeTo(flutterTree)
        settings(flutter: flutter).writeTo(gradleTree)

        then:
        flutterTree.get('flutter.platform') == 'apk'
        flutterTree.get('build.credentialsId') == ['sign', 'prod-license', 'test-license']
        flutterTree.get('build.gradle') == null
        gradleTree.get('flutter') == null
    }

    def "each section reports its problems under its own name"() {
        given:
        def settings = settings(build: build(javaPath: null), deployment: deployment(target: DeployTarget.OPENSHIFT),
                appScan: appScan(dastEnabled: true), scm: ScmSettings.of('https://bitbucket.bbh.com/scm/ta/cert.git', null),
                sonar: SonarSettings.of('Cert', 'cert', null),
                unitTests: new UnitTestSettings(null, '**/TEST-*.xml', null, null, false, null))
        def problems = new ValidationProblems()

        when:
        settings.validate(problems.at('services[3]'))

        then:
        problems.list()*.field == ['services[3].build.javaPath', 'services[3].unitTests.command.tasks',
                                   'services[3].deployment.appName', 'services[3].deployment.artifactName',
                                   'services[3].appScan.dastTargetUrl', 'services[3].sonar.command.tasks',
                                   'services[3].scm.credentialsId']
    }

    def "a Maven service on a VM needs the delivery goals and a Flutter service its signing credentials"() {
        given:
        def problems = new ValidationProblems()

        when:
        settings(build: build(tool: BuildTool.MAVEN), delivery: ToolCommand.NONE).validate(problems.at('maven'))
        settings(build: build(tool: BuildTool.FLUTTER, javaPath: null)).validate(problems.at('flutter'))

        then:
        problems.list()*.field == ['maven.delivery.tasks', 'flutter.flutter.signingPasswordCredentialsId',
                                   'flutter.flutter.prodLicenseCredentialsId', 'flutter.flutter.testLicenseCredentialsId']
    }

    def "a remote test job given as a path must name its Jenkins"() {
        given:
        def problems = new ValidationProblems()
        def jobs = [new TestJob(TestStage.SMOKE, null, TestJobType.REMOTE, 'CERT/smoke', null, null, null, null, null),
                    new TestJob(TestStage.SMOKE, null, TestJobType.REMOTE, 'https://jenkins.qc/job/smoke/', null, null,
                            null, null, null),
                    new TestJob(TestStage.REGRESSION, null, TestJobType.REMOTE, 'CERT/regression', null, null, 'qc', null,
                            null)]

        when:
        settings(testJobs: jobs).validate(problems)

        then:
        problems.list()*.field == ['testJobs[0].remoteJenkins']
    }

    def "a missing metrics project is filled in from the product code and the service name"() {
        given:
        def plain = settings()
        def explicit = settings(metrics: new MetricsSettings(false, 'cert-scanner', 'uat'))

        expect:
        plain.withDefaultMetricsProject('CERT', 'gui').metrics() == new MetricsSettings(true, 'CERT-gui', 'test')
        plain.withDefaultMetricsProject('CERT', 'gui').build() == plain.build()
        explicit.withDefaultMetricsProject('CERT', 'gui').is(explicit)
    }

    def "a service cannot do without its #section settings"() {
        when:
        new ServiceSettings(section == 'build' ? null : build(), null, null, null,
                section == 'deployment' ? null : deployment(), null, null, null, null, null,
                section == 'AppScan' ? null : appScan(), null, null, null, null, null, null)

        then:
        def e = thrown(NullPointerException)
        e.message == "a service needs its $section settings"

        where:
        section << ['build', 'deployment', 'AppScan']
    }
}
