package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Fixtures.APP_ID
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.fullSettings
import static com.bbh.itss.dso.portal.support.Fixtures.settings

class ServiceSettingsSpec extends Specification {

    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'
    static final SshTarget RD_HOST = new SshTarget('rd.host', null, null, null, null)
    static final OpenShiftTarget RD_PROJECT = new OpenShiftTarget('cert-build', null, null, null, null, null, null,
            null, null, 'cert-rd', null, null, false, null, null, null, null, null, null, null, null)
    static final UrbanCodeApplicationSettings UCD_APP = UrbanCodeApplicationSettings.of('Cert', 1, ['RD'], null,
            [new UrbanCodeComponent('cert-gui', 'build', '*.war', null, null, null, false, null, null, null, null, null)])

    def "sections left out take their defaults"() {
        expect:
        new ServiceSettings(build(), null, null, null, deployment(), null, null, null, null, null, appScan(), null, null,
                null, null, null, null, null) == new ServiceSettings(build(), UnitTestSettings.NONE,
                TestSettings.DEFAULTS, [], deployment(), ToolCommand.NONE, UrbanCodeSettings.DEFAULTS, [], [:], [:],
                appScan(), SonarSettings.NONE, NexusIqSettings.NONE, null, ScmSettings.NONE, GoldenFixPolicy.INHERITED,
                MetricsSettings.DEFAULTS, FlutterSettings.NONE)
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
        when:
        def tree = written(settings(delivery: command(['publish']), urbanCodeApplications: [UCD_APP],
                sshTargets: [(Region.RD): RD_HOST], openShiftTargets: [(Region.RD): RD_PROJECT]))

        then:
        tree.get('build.gradle.tasks') == ['clean', 'build']
        tree.get('delivery') == null
        tree.get('deploy.vm.rd') == [host: 'rd.host']
        tree.get('deploy.vm.dod.applications')*.applicationName == ['Cert']
        tree.get('deploy.openshift') == null
        tree.get('flutter') == null
        written(settings(build: build(tool: BuildTool.MAVEN), delivery: command(['deploy:deploy-file'], ['-Did=bbh'])))
                .get('delivery') == [maven: [goals: ['deploy:deploy-file'], flags: ['-Did=bbh']]]
    }

    def "a service on OpenShift writes its OpenShift projects and nothing of the VM deployment"() {
        when:
        def tree = written(settings(deployment: deployment(target: DeployTarget.OPENSHIFT, appName: 'cert',
                artifactName: 'cert.jar'), urbanCodeApplications: [UCD_APP], sshTargets: [(Region.RD): RD_HOST],
                openShiftTargets: [(Region.RD): RD_PROJECT]))

        then:
        tree.get('deploy.openshift.rd') == [projectBuildR: 'cert-build', projectDeploymentR: 'cert-rd']
        tree.get('deploy.vm') == null
    }

    def "only a Flutter service writes its Flutter section"() {
        given:
        def flutter = new FlutterSettings(FlutterPlatform.APK, ['app'], [], [], [], 'sign', 'prod-license',
                'test-license', null, null, null, null, null, false, null, null)

        when:
        def flutterTree = written(settings(build: build(tool: BuildTool.FLUTTER, javaPath: null), flutter: flutter))

        then:
        flutterTree.get('flutter.platform') == 'apk'
        flutterTree.get('build.credentialsId') == ['sign', 'prod-license', 'test-license']
        flutterTree.get('build.gradle') == null
        written(settings(flutter: flutter)).get('flutter') == null
    }

    def "each section reports its problems under its own name"() {
        given:
        def problems = new ValidationProblems()

        when:
        settings(build: build(javaPath: null), deployment: deployment(target: DeployTarget.OPENSHIFT),
                appScan: appScan(dastEnabled: true), scm: ScmSettings.of(REPO, null),
                sonar: SonarSettings.of('Cert', 'cert', null),
                unitTests: new UnitTestSettings(null, '**/TEST-*.xml', null, null, false, null))
                .validate(problems.at('services[3]'))

        then:
        problems.list()*.field == ['build.javaPath', 'unitTests.command.tasks', 'deployment.appName',
                                   'deployment.artifactName', 'openShiftTargets[RD].projectBuild',
                                   'openShiftTargets[RD].buildConfigPath', 'openShiftTargets[RD].dockerFilePath',
                                   'openShiftTargets[RD].buildContext', 'openShiftTargets[RD].dockerRepoPush',
                                   'openShiftTargets[RD].nexusAuthFile', 'appScan.dastTargetUrl',
                                   'sonar.command.tasks', 'scm.credentialsId'].collect { "services[3].$it" as String }
    }

    def "a Maven service on a VM needs its artifact path and delivery goals and a Flutter service what its stages read"() {
        expect:
        reported(settings(build: build(tool: BuildTool.MAVEN), delivery: ToolCommand.NONE))*.field ==
                ['build.buildPath', 'delivery.tasks']
        reported(settings(build: build(tool: BuildTool.MAVEN), delivery: ToolCommand.NONE))[0].message ==
                'is required for Maven on VMs: the Nexus delivery publishes the artifact found there'
        reported(settings(build: build(tool: BuildTool.FLUTTER, javaPath: null)))*.field ==
                ['build.javaPath', 'flutter.modules', 'flutter.testModules', 'flutter.signingPasswordCredentialsId',
                 'flutter.prodLicenseCredentialsId', 'flutter.testLicenseCredentialsId', 'flutter.deliveryGroup',
                 'flutter.deliveryArtifact', 'flutter.deliveryPlugin']
        reported(settings(build: build(tool: BuildTool.MAVEN), delivery: ToolCommand.NONE,
                deployment: deployment(target: DeployTarget.OPENSHIFT, appName: 'cert', artifactName: 'cert.jar'),
                openShiftTargets: [(Region.RD): fullSettings().openShiftTargets()[Region.RD]])) == []
    }

    def "an OpenShift service needs an RD target that can build its image"() {
        given:
        def rd = new OpenShiftTarget('cert-build', null, 'Dockerfile', '.', null, 'push.bbh.com/cert', null, null, null,
                'cert-rd', null, null, false, null, null, null, null, null, null, null, null)

        when:
        def problems = reported(settings(deployment: deployment(target: DeployTarget.OPENSHIFT, appName: 'cert',
                artifactName: 'cert.jar'), openShiftTargets: [(Region.RD): rd]))

        then:
        problems*.field == ['openShiftTargets[RD].buildConfigPath', 'openShiftTargets[RD].nexusAuthFile']
        problems*.message.unique() ==
                ['is required for OpenShift: the Nexus snapshot delivery builds the image in the RD project']
    }

    def "every Nexus IQ application of the service needs its name and scan patterns"() {
        expect:
        reported(settings(nexusIqApplications: applications))*.field == fields

        where:
        applications                                  || fields
        [NexusIqApplication.of(null, ['**/*.war'])]   || ['nexusIqApplications[0].application']
        [NexusIqApplication.of('cert', [])]           || ['nexusIqApplications[0].scanPatterns']
        [NexusIqApplication.of('cert', ['**/*.war'])] || []
        []                                            || []
    }

    def "an UrbanCode application needs components that name their folder and files"() {
        given:
        def applications = [UrbanCodeApplicationSettings.of('Cert', 1, [], null, []),
                            UrbanCodeApplicationSettings.of('Cert Batch', 2, [], null,
                                    [new UrbanCodeComponent('batch', ' ', null, null, null, null, true, null, null, null, null, null),
                                     new UrbanCodeComponent('config', 'config', '*.yml', null, null, null, true, null, null, null, null, null)])]

        expect:
        reported(settings(urbanCodeApplications: applications))*.field == ['urbanCodeApplications[0].components',
                                                                          'urbanCodeApplications[1].components[0].baseDir',
                                                                          'urbanCodeApplications[1].components[0].fileIncludePatterns']
    }

    def "test job parameters '#parameters' are one NAME=value per line: #valid"() {
        when:
        def problems = reported(settings(testJobs: [new TestJob(TestStage.SMOKE, null, TestJobType.LOCAL, 'CERT/smoke',
                null, parameters, null, null, null, null, null, false, false, false, false, false, false)]))

        then:
        problems.collect { [it.field, it.message] } ==
                (valid ? [] : [['testJobs[0].parameters', 'write one parameter per line as NAME=value']])

        where:
        parameters                      || valid
        'ENV=rd\nSUITE=critical'        || true
        'ENV=rd\n\n  \nlog.level-x=a=b' || true
        'ENV=rd,SUITE=critical'         || true
        'ENV=rd\nSUITE critical'        || false
        '1ENV=rd'                       || false
        'ENV=rd\n =x'                   || false
        null                            || true
    }

    def "list values that do not fit their column are refused field by field"() {
        given:
        def longFlags = (1..12).collect { "-Dproperty.$it=${'v' * 200}".toString() }
        def longDirs = (1..20).collect { "${'d' * 150}/$it".toString() }
        def reviewers = (1..3).collect { ("reviewer-$it-" + '\u017c\u00f3\u0142\u0107' * 120).toString() }
        def command = new ToolCommand(['build'], longFlags, null, null, [], null, false)

        when:
        def problems = reported(settings(build: build(command: command),
                appScan: new AppScanSettings(APP_ID, null, longDirs, ['test'], false, false, false, false, null, command,
                        false, null, null, null, null),
                scm: new ScmSettings(REPO, 'bb-creds', null, null, null, null, reviewers, null, null, null, null),
                goldenFix: new GoldenFixPolicy(null, null, null, [], [], longDirs, null, null, null, null, null, null,
                        null, null, null, null, null)))

        then:
        problems*.field == ['build.command.flags', 'appScan.includedDirs', 'appScan.compileCommand.flags',
                            'scm.reviewers', 'goldenFix.excludeDirs']
        problems[0].message == 'is too long: all entries together may take at most 2000 bytes'
    }

    def "a remote test job given as a path must name its Jenkins"() {
        given:
        def jobs = [TestJob.of(TestStage.SMOKE, null, TestJobType.REMOTE, 'CERT/smoke', null),
                    new TestJob(TestStage.SMOKE, null, TestJobType.REMOTE, 'https://jenkins.qc/job/smoke/', null, null,
                            null, null, null, null, null, false, false, false, false, false, false),
                    new TestJob(TestStage.REGRESSION, null, TestJobType.REMOTE, 'CERT/regression', null, null, 'qc', null,
                            null, null, null, false, false, false, false, false, false)]

        expect:
        reported(settings(testJobs: jobs))*.field == ['testJobs[0].remoteJenkins']
    }

    def "a missing metrics project is filled in from the product code and the service name"() {
        given:
        def plain = settings()
        def explicit = settings(metrics: new MetricsSettings(false, 'cert-scanner', 'uat', null, null))

        expect:
        plain.withDefaultMetricsProject('CERT', 'gui').metrics() == new MetricsSettings(true, 'CERT-gui', 'test', null, null)
        plain.withDefaultMetricsProject('CERT', 'gui').build() == plain.build()
        explicit.withDefaultMetricsProject('CERT', 'gui').is(explicit)
    }

    def "a service cannot do without its #section settings"() {
        when:
        new ServiceSettings(section == 'build' ? null : build(), null, null, null,
                section == 'deployment' ? null : deployment(), null, null, null, null, null,
                section == 'AppScan' ? null : appScan(), null, null, null, null, null, null, null)

        then:
        def e = thrown(NullPointerException)
        e.message == "a service needs its $section settings"

        where:
        section << ['build', 'deployment', 'AppScan']
    }

    private static ConfigTree written(ServiceSettings settings) {
        def tree = new ConfigTree()
        settings.writeTo(tree)
        tree
    }

    private static List reported(ServiceSettings settings) {
        def problems = new ValidationProblems()
        settings.validate(problems)
        problems.list()
    }
}
