package com.bbh.itss.dso.portal.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.catalog.Region.QC
import static com.bbh.itss.dso.portal.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.Fixtures.appScan
import static com.bbh.itss.dso.portal.support.Fixtures.build
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.withId

class ServiceMappingSpec extends Specification {

    static final List<String> SECTIONS = ServiceSettings.getRecordComponents()*.name

    def "a request with every section passes each one to the settings of the same name"() {
        given:
        def request = fullRequest()

        when:
        def settings = request.settings()

        then:
        SECTIONS.size() == 17
        SECTIONS.findAll { settings."$it"() != request."$it"() } == []
        settings.sshTargets() == [(RD): request.sshTargets()[RD], (QC): request.sshTargets()[QC]]
        settings.openShiftTargets().keySet() == [QC] as Set
    }

    def "a request with only the required sections gets the defaults of the others"() {
        given:
        def request = new ServiceRequest(null, 'gui', null, build(), null, null, null, deployment(), null, null, null, null,
                null, appScan(), null, null, null, null, null, null)

        expect:
        request.settings() == ServiceSettings.of(build(), deployment(), appScan())
    }

    def "a stored service is answered with its id, name, description and every section"() {
        given:
        def request = fullRequest()
        def service = withId(product().addService('gui', ' Angular front end ', 0, request.settings()), 12L)

        when:
        def response = ServiceResponse.from(service)

        then:
        response.id() == 12L
        response.name() == 'gui'
        response.description() == 'Angular front end'
        SECTIONS.findAll { response."$it"() != service.settings()."$it"() } == []
        response.testJobs() == request.testJobs()
        response.urbanCodeApplications() == request.urbanCodeApplications()
        response.goldenFix() == request.goldenFix()
        response.flutter() == request.flutter()
    }

    def "a response has the shape of a request"() {
        expect:
        ServiceResponse.getRecordComponents()*.name == ServiceRequest.getRecordComponents()*.name
        ServiceResponse.getRecordComponents()*.type == ServiceRequest.getRecordComponents()*.type
        ServiceRequest.getRecordComponents()*.name.drop(3) == SECTIONS
    }

    private static ServiceRequest fullRequest() {
        new ServiceRequest(7L, 'gui', 'Angular front end',
                build(tool: BuildTool.MAVEN, buildPath: 'target/gui.war'),
                new UnitTestSettings(command(['test']), '**/TEST-*.xml', null, null, true, null),
                new TestSettings(5, 1, 2, 3),
                [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', 10, null, null, null, null)],
                deployment(appName: 'gui'),
                command(['deploy:deploy-file']),
                new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'desc', 'a=b'),
                [new UrbanCodeApplicationSettings('Cert', 1, ['RD'], 'snap', [])],
                [(RD): new SshTarget('rd.host', null, null, null, null), (QC): new SshTarget('qc.host', null, null, null, null)],
                [(QC): new OpenShiftTarget(null, null, null, null, null, null, 'pull/cert', null, null, 'cert-qc', null, null,
                        true, null, null, null, null, null, null)],
                appScan(dastEnabled: true, dastTargetUrl: 'https://rdl1.testbbh.com'),
                SonarSettings.of('Cert', 'cert-gui', command(['sonar:sonar'])),
                NexusIqSettings.of('cert', ['**/*.war']),
                ScmSettings.of('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds'),
                GoldenFixPolicy.inherit(false),
                new MetricsSettings(false, 'cert-gui', 'qc'),
                new FlutterSettings(FlutterPlatform.WEB, ['app'], [], [], [], 's', 'p', 't', null, null, null, null, null, true,
                        null, null))
    }
}
