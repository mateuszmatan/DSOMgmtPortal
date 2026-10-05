package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.TestJob
import com.bbh.itss.dso.portal.domain.catalog.TestSettings
import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment

class ServiceMappingSpec extends Specification {

    static final List<String> SECTIONS = ServiceSettings.getRecordComponents()*.name

    def "a stored service is answered with its id, name, description and every section"() {
        given:
        def settings = fullSettings()

        when:
        def response = ServiceResponse.from(new Service(12L, 'gui', ' Angular front end ', 0, settings))

        then:
        response.id() == 12L
        response.name() == 'gui'
        response.description() == 'Angular front end'
        response.build() == BuildSettingsDto.from(settings.build())
        response.testJobs() == settings.testJobs().collect { TestJobDto.from(it) }
        response.urbanCodeApplications()[0].components()[0] ==
                UrbanCodeComponentDto.from(settings.urbanCodeApplications()[0].components()[0])
        response.sshTargets().keySet() as List == [RD, QC]
        response.openShiftTargets() == [(QC): OpenShiftTargetDto.from(settings.openShiftTargets()[QC])]
        response.scm().projectKey() == 'TA'
        response.scm().repoSlug() == 'cert-gui'
        response.goldenFix() == GoldenFixPolicyDto.from(settings.goldenFix())
        response.flutter() == FlutterSettingsDto.from(settings.flutter())
    }

    def "a request with every section passes each one to the settings of the same name"() {
        given:
        def settings = fullSettings()

        when:
        def request = requestOf(ServiceResponse.from(new Service(7L, 'gui', 'Angular front end', 0, settings)))

        then:
        SECTIONS.size() == 17
        request.settings() == settings
        with(request.toCommand()) {
            id() == 7L
            name() == 'gui'
            description() == 'Angular front end'
            it.settings() == settings
        }
    }

    def "a request with only the required sections gets the defaults of the others"() {
        given:
        def request = new ServiceRequest(null, 'gui', null, BuildSettingsDto.from(build()), null, null, null,
                DeploymentSettingsDto.from(deployment()), null, null, null, null, null, AppScanSettingsDto.from(appScan()),
                null, null, null, null, null, null)

        expect:
        request.settings() == ServiceSettings.of(build(), deployment(), appScan())
    }

    def "deployment targets left empty in a request are dropped"() {
        given:
        def targets = new HashMap()
        targets.put(RD, null)
        targets.put(QC, SshTargetDto.from(new SshTarget('qc.host', null, null, null, null)))
        def request = new ServiceRequest(null, 'gui', null, BuildSettingsDto.from(build()), null, null, null,
                DeploymentSettingsDto.from(deployment()), null, null, null, targets, [:],
                AppScanSettingsDto.from(appScan()), null, null, null, null, null, null)

        expect:
        request.settings().sshTargets() == [(QC): new SshTarget('qc.host', null, null, null, null)]
        request.settings().openShiftTargets() == [:]
    }

    def "a response has the shape of a request"() {
        expect:
        ServiceResponse.getRecordComponents()*.name == ServiceRequest.getRecordComponents()*.name
        ServiceResponse.getRecordComponents()*.type == ServiceRequest.getRecordComponents()*.type
        ServiceRequest.getRecordComponents()*.name.drop(3) == SECTIONS
    }

    static ServiceRequest requestOf(ServiceResponse response) {
        new ServiceRequest(*ServiceResponse.getRecordComponents().collect { it.accessor.invoke(response) })
    }

    static ServiceSettings fullSettings() {
        new ServiceSettings(build(tool: BuildTool.MAVEN, buildPath: 'target/gui.war'),
                new UnitTestSettings(command(['test']), '**/TEST-*.xml', null, null, true, null),
                new TestSettings(5, 1, 2, 3),
                [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', 10, null, null, null, null)],
                deployment(appName: 'gui'),
                command(['deploy:deploy-file']),
                new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'desc', 'a=b'),
                [new UrbanCodeApplicationSettings('Cert', 1, ['RD'], 'snap',
                        [new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', null, null, null, false)])],
                [(QC): new SshTarget('qc.host', null, null, null, null), (RD): new SshTarget('rd.host', null, null, null, null)],
                [(QC): new OpenShiftTarget(null, null, null, null, null, null, 'pull/cert', null, null, 'cert-qc', null, null,
                        true, null, null, null, null, null, null)],
                appScan(dastEnabled: true, dastTargetUrl: 'https://rdl1.testbbh.com', compileCommand: command(['compile'])),
                SonarSettings.of('Cert', 'cert-gui', command(['sonar:sonar'])),
                NexusIqSettings.of('cert', ['**/*.war']),
                new ScmSettings('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds', null, null, null, null, null,
                        'https://bitbucket.bbh.com/rest/api/1.0', 'ta-workspace', 'TA', 'cert-gui'),
                GoldenFixPolicy.inherit(false),
                new MetricsSettings(false, 'cert-gui', 'qc'),
                new FlutterSettings(FlutterPlatform.WEB, ['app'], [], [], [], 's', 'p', 't', null, null, null, null, null, true,
                        null, null))
    }
}
