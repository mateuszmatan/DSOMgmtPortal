package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.catalog.port.in.ManageProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.TestJob
import com.bbh.itss.dso.portal.domain.catalog.TestJobType
import com.bbh.itss.dso.portal.domain.catalog.TestSettings
import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.catalog.Region.QC
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD
import static com.bbh.itss.dso.portal.support.ApiJson.APP_ID
import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.product as productJson
import static com.bbh.itss.dso.portal.support.ApiJson.service as serviceJson
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static com.bbh.itss.dso.portal.support.Fixtures.JDK
import static com.bbh.itss.dso.portal.support.Fixtures.command
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class ProductControllerSpec extends Specification {

    static final String REGRESSION_URL = 'https://jenkins-qc.bbh.com/job/CERT/job/regression/'

    QueryProductsUseCase queries = Mock()
    ManageProductsUseCase products = Mock()
    MockMvc mvc = WebMvc.of(new ProductController(queries, products))

    def "lists the products matching the search"() {
        given:
        def updated = Instant.parse('2026-10-03T10:00:00Z')

        when:
        def response = mvc.perform(get('/api/products').param('search', 'cert')).andReturn().response

        then:
        1 * queries.list('cert') >> [new ProductSummaryView(1L, 'CERT', 'CertScanner', 'Scans', 'TA', 2, 3, 1, updated)]
        response.status == 200
        parse(response.contentAsString) == [[id                 : 1, code: 'CERT', name: 'CertScanner', description: 'Scans',
                                             ownerTeam          : 'TA', serviceCount: 2, pipelineCount: 3,
                                             activePipelineCount: 1, updatedAt: '2026-10-03T10:00:00Z']]
    }

    def "returns a product with its services"() {
        when:
        def response = mvc.perform(get('/api/products/5')).andReturn().response

        then:
        1 * queries.get(5L) >> product(id: 5, ownerTeam: 'TA', contactEmail: 'ta@bbh.com', version: 3,
                services: [[name: 'gui', id: 10]])
        response.status == 200
        with(parse(response.contentAsString)) {
            id == 5
            code == 'CERT'
            ownerTeam == 'TA'
            contactEmail == 'ta@bbh.com'
            version == 3
            createdAt == '2026-10-01T08:00:00Z'
            updatedAt == '2026-10-02T09:30:00Z'
            services[0].id == 10
            services[0].name == 'gui'
            services[0].build.tool == 'GRADLE'
            services[0].metrics.influxProject == 'CERT-gui'
            appScan == [keyId: 'bbh_key-id', secretCredentialsId: 'hcl-app-scan-account']
        }
    }

    def "an unknown product is 404"() {
        when:
        def response = mvc.perform(get('/api/products/5')).andReturn().response

        then:
        1 * queries.get(5L) >> { throw NotFoundException.of('Product', 5L) }
        response.status == 404
        parse(response.contentAsString).detail == 'Product 5 does not exist'
    }

    def "creating a product answers 201 with its location"() {
        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson(description: ' Scans certificates ')))).andReturn().response

        then:
        1 * products.create({ ProductCommand c ->
            c.version() == null && c.details() == new ProductDetails('CERT', 'CertScanner', 'Scans certificates', null, null) &&
                    c.appScan().keyId() == 'bbh_key-id' && c.services()*.name() == ['gui'] && c.services()[0].id() == null
        }) >> product(id: 9)
        response.status == 201
        response.getHeader('Location') == 'http://localhost/api/products/9'
        parse(response.contentAsString).id == 9
    }

    def "an invalid request is 400 with every invalid field"() {
        given:
        def body = productJson(code: 'cert', contactEmail: 'not-an-email', services: [
                serviceJson(build: null, appScan: [applicationId: 'nope'], metrics: [influxProject: 'has space'])])

        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body))).andReturn().response

        then:
        0 * products.create(_)
        response.status == 400
        parse(response.contentAsString).errors*.field.sort() == ['code', 'contactEmail', 'services[0].appScan.applicationId',
                                                                 'services[0].build', 'services[0].metrics.influxProject']
    }

    def "a product without its AppScan account or service list is 400"() {
        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson(appScan: null, services: null)))).andReturn().response

        then:
        0 * products.create(_)
        response.status == 400
        parse(response.contentAsString).errors*.field.sort() == ['appScan', 'services']
    }

    def "a body that is not JSON is 400"() {
        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content('{"code": ')).andReturn().response

        then:
        response.status == 400
        parse(response.contentAsString).title == 'Malformed request'
    }

    def "updating a product returns the stored version"() {
        when:
        def response = mvc.perform(put('/api/products/5').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson(name: 'CertScanner 2', version: 0)))).andReturn().response

        then:
        1 * products.update(5L, { ProductCommand c -> c.version() == 0L }) >> product(id: 5, name: 'CertScanner 2', version: 1)
        response.status == 200
        parse(response.contentAsString).name == 'CertScanner 2'
        parse(response.contentAsString).version == 1
    }

    def "a conflicting update is 409: #detail"() {
        when:
        def response = mvc.perform(put('/api/products/5').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(productJson()))).andReturn().response

        then:
        1 * products.update(5L, _) >> { throw failure }
        response.status == 409
        parse(response.contentAsString).detail == detail

        where:
        failure                                                         || detail
        new ConflictException('A product named CertScanner already exists') || 'A product named CertScanner already exists'
        ConflictException.staleVersion()                                || ConflictException.STALE_VERSION
    }

    def "deleting a product answers 204"() {
        when:
        def response = mvc.perform(delete('/api/products/5')).andReturn().response

        then:
        1 * products.delete(5L)
        response.status == 204
    }

    def "a service with test jobs, UrbanCode applications, deployment targets, GoldenFix and Flutter reaches the catalog as entered"() {
        given:
        ProductCommand received = null
        def body = productJson(services: [serviceJson(
                build: [tool: 'GRADLE', javaPath: JDK, buildPath: 'build/libs/gui.war',
                        command: [tasks: ['clean', 'build'], flags: ['--no-daemon'], environment: ['CI=true']]],
                unitTests: [command: [tasks: ['test']], resultPattern: '**/TEST-*.xml', allowEmptyResults: true],
                tests: [maxParallel: 4, smokeMaxParallel: 2],
                testJobs: [[stage: 'SMOKE', name: 'smoke', job: 'CERT/gui-smoke', timeoutMinutes: 15],
                           [stage: 'REGRESSION', type: 'REMOTE', job: REGRESSION_URL, credentialsId: 'jenkins-qc']],
                urbanCode: [siteName: 'BBH-RD', skipWait: true],
                urbanCodeApplications: [[applicationName: 'Cert', order: 1, environments: ['RD'],
                                         components     : [[componentName: 'cert-gui', baseDir: 'build/libs',
                                                            fileIncludePatterns: '*.war']]]],
                sshTargets: [RD: [host: 'rdltaapps1.testbbh.com', user: 'dsoadm'], QC: [host: 'qcltaapps1.testbbh.com']],
                openShiftTargets: [RD: [projectBuild: 'cert-build', projectDeployment: 'cert-rd', skipConfigDeploy: true]],
                goldenFix: [enabled: false, minThreatLevel: 8, ecosystems: ['maven', 'npm'], verifyGradleCommand: './gradlew check'],
                flutter: [platform: 'APK', modules: ['app'], signingPasswordCredentialsId: 'sign',
                          prodLicenseCredentialsId: 'prod', testLicenseCredentialsId: 'test'])])

        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body))).andReturn().response

        then:
        1 * products.create(_) >> { ProductCommand c -> received = c; product(id: 9) }
        response.status == 201
        with(received.services()[0].settings()) {
            build() == new BuildSettings(BuildTool.GRADLE, null, JDK, false, 'build/libs/gui.war',
                    new ToolCommand(['clean', 'build'], ['--no-daemon'], null, null, ['CI=true']))
            unitTests() == new UnitTestSettings(command(['test']), '**/TEST-*.xml', null, null, true, null)
            tests() == new TestSettings(4, 2, null, null)
            testJobs() == [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', 15, null, null, null, null),
                           new TestJob(TestStage.REGRESSION, null, TestJobType.REMOTE, REGRESSION_URL, null, null, null, null,
                                   'jenkins-qc')]
            urbanCode() == new UrbanCodeSettings('BBH-RD', null, true, true, false, true, false, null, null)
            urbanCodeApplications() == [new UrbanCodeApplicationSettings('Cert', 1, ['RD'], null,
                    [new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', null, null, null, true)])]
            sshTargets() == [(RD): new SshTarget('rdltaapps1.testbbh.com', 'dsoadm', null, null, null),
                             (QC): new SshTarget('qcltaapps1.testbbh.com', null, null, null, null)]
            openShiftTargets() == [(RD): new OpenShiftTarget('cert-build', null, null, null, null, null, null, null, null,
                    'cert-rd', null, null, true, null, null, null, null, null, null)]
            goldenFix() == new GoldenFixPolicy(false, null, 8, ['maven', 'npm'], [], [], null, null, null, null,
                    './gradlew check', null, null, null, null, null, null)
            flutter() == new FlutterSettings(FlutterPlatform.APK, ['app'], [], [], [], 'sign', 'prod', 'test', null, null,
                    null, null, null, false, null, null)
            delivery() == ToolCommand.NONE
            sonar() == SonarSettings.NONE
        }
    }

    def "a stored service is answered with its test jobs, UrbanCode applications, deployment targets, GoldenFix and Flutter"() {
        given:
        def stored = product(id: 5, services: [[id: 10, settings: settings(
                testJobs: [new TestJob(TestStage.SMOKE, 'smoke', null, 'CERT/gui-smoke', 15, null, null, null, null),
                           new TestJob(TestStage.REGRESSION, null, TestJobType.REMOTE, REGRESSION_URL, null, null, null, null,
                                   null)],
                urbanCodeApplications: [new UrbanCodeApplicationSettings('Cert', 1, ['RD'], null,
                        [new UrbanCodeComponent('cert-gui', 'build/libs', null, null, null, null, false)])],
                sshTargets: [(QC): new SshTarget('qc.host', null, null, null, null),
                             (RD): new SshTarget('rd.host', 'dsoadm', null, null, null)],
                openShiftTargets: [(RD): new OpenShiftTarget('cert-build', null, null, null, null, null, null, null, null,
                        'cert-rd', null, null, false, null, null, null, null, null, null)],
                goldenFix: new GoldenFixPolicy(false, true, 8, ['maven'], [], [], null, null, null, null, null, null, null,
                        null, null, null, null),
                flutter: new FlutterSettings(FlutterPlatform.WEB, ['app'], [], [], [], null, null, null, null, null, null, null,
                        null, false, null, null))]])

        when:
        def response = mvc.perform(get('/api/products/5')).andReturn().response

        then:
        1 * queries.get(5L) >> stored
        response.status == 200
        with(parse(response.contentAsString).services[0]) {
            testJobs*.stage == ['SMOKE', 'REGRESSION']
            testJobs*.job == ['CERT/gui-smoke', ProductControllerSpec.REGRESSION_URL]
            testJobs[0].timeoutMinutes == 15
            testJobs[1].type == 'REMOTE'
            urbanCodeApplications[0].applicationName == 'Cert'
            urbanCodeApplications[0].environments == ['RD']
            urbanCodeApplications[0].components[0].componentName == 'cert-gui'
            urbanCodeApplications[0].components[0].incrementalVersion == false
            sshTargets.keySet() as List == ['RD', 'QC']
            sshTargets.RD.host == 'rd.host'
            sshTargets.RD.user == 'dsoadm'
            sshTargets.QC.host == 'qc.host'
            openShiftTargets.keySet() as List == ['RD']
            openShiftTargets.RD.projectBuild == 'cert-build'
            openShiftTargets.RD.projectDeployment == 'cert-rd'
            goldenFix.enabled == false
            goldenFix.onlyDirectDependencies == true
            goldenFix.minThreatLevel == 8
            goldenFix.ecosystems == ['maven']
            flutter.platform == 'WEB'
            flutter.modules == ['app']
            urbanCode.deployWithSnapshot == true
            tests.maxParallel == null
            delivery.tasks == []
            !containsKey('empty')
        }
    }

    def "invalid nested values are reported with the path of each field"() {
        given:
        def body = productJson(services: [serviceJson(
                appScan: [applicationId: APP_ID, includedDirs: ['src,lib']],
                unitTests: [command: [environment: ['CI']]],
                tests: [smokeMaxParallel: 0],
                testJobs: [[stage: 'SMOKE', job: ' ', timeoutMinutes: 0]],
                urbanCodeApplications: [[applicationName: 'Cert', environments: ['R D'], components: [[componentName: ' ']]]],
                sshTargets: [RD: [host: 'rd host'], QC: [host: 'qcltaapps1.testbbh.com']],
                openShiftTargets: [QC: [deploymentRepoUrl: 'bitbucket/ta/deploy']],
                scm: [repositoryUrl: 'https://bitbucket.bbh.com/scm/ta/cert.git', reviewers: ['john doe'],
                      apiUrl: 'bitbucket.bbh.com/rest/api/1.0', repoSlug: 'cert scanner'],
                goldenFix: [minThreatLevel: 11, ecosystems: ['gradle']],
                flutter: [modules: ['my module']])])

        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body))).andReturn().response
        def errors = parse(response.contentAsString).errors

        then:
        0 * products.create(_)
        response.status == 400
        errors*.field.sort() == ['services[0].appScan.includedDirs[0]',
                                 'services[0].flutter.modules[0]',
                                 'services[0].goldenFix.ecosystems[0]',
                                 'services[0].goldenFix.minThreatLevel',
                                 'services[0].openShiftTargets[QC].deploymentRepoUrl',
                                 'services[0].scm.apiUrl',
                                 'services[0].scm.repoSlug',
                                 'services[0].scm.reviewers[0]',
                                 'services[0].sshTargets[RD].host',
                                 'services[0].testJobs[0].job',
                                 'services[0].testJobs[0].timeoutMinutes',
                                 'services[0].tests.smokeMaxParallel',
                                 'services[0].unitTests.command.environment[0]',
                                 'services[0].urbanCodeApplications[0].components[0].componentName',
                                 'services[0].urbanCodeApplications[0].environments[0]'].sort()
        def messages = errors.collectEntries { [(it.field): it.message] }
        messages['services[0].sshTargets[RD].host'] == 'must be a host name such as rdltaapps1.testbbh.com'
        messages['services[0].openShiftTargets[QC].deploymentRepoUrl'] == 'must be a Git repository URL'
        messages['services[0].unitTests.command.environment[0]'] == 'write each variable as NAME=value'
        messages['services[0].goldenFix.ecosystems[0]'] == 'must be maven, npm, pypi or pub'
        messages['services[0].flutter.modules[0]'] == 'must be a module folder name'
        messages['services[0].scm.apiUrl'] == 'must be an http or https URL'
        messages['services[0].scm.repoSlug'] == 'must not contain whitespace'
        parse(response.contentAsString).detail == '15 fields are invalid'
    }

    def "an UrbanCode application listing a missing component is rejected"() {
        given:
        def body = productJson(services: [serviceJson(urbanCodeApplications: [[applicationName: 'Cert', components: [null]]])])

        when:
        def response = mvc.perform(post('/api/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body))).andReturn().response

        then:
        0 * products.create(_)
        response.status == 400
    }
}
