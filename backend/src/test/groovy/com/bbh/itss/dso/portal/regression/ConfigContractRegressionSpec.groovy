package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service

/**
 * Pins the config.yaml the portal renders, which the DevSecOps library will read instead of the file in the
 * product's repository. A difference means the contract changed: check it against the library and, when it is
 * intended, regenerate the expected files with {@code -Dregression.updateExpected=true}.
 */
class ConfigContractRegressionSpec extends PortalSpecification {

    static final String EXPECTED_DIR = 'src/test/resources/regression'

    static final Map PAYMENTS = product(code: 'CONTRACT', name: 'Contract Payments Hub', description: 'Payment orchestration',
            ownerTeam: 'Payments Engineering', contactEmail: 'payments-eng@bbh.com',
            appScan: [keyId: 'bbh_1c2d3e4f-0000-4abc-9def-123456789abc', secretCredentialsId: 'hcl-app-scan-account'],
            services: [
                    service(name: 'gateway', description: 'Public payment API',
                            build: [tool: 'MAVEN', sourceDir: 'gateway', javaPath: '/usr/lib/jvm/java-17-openjdk'],
                            deployment: [target: 'OPENSHIFT', appName: 'payhub-gateway', artifactName: 'payhub-gateway.jar'],
                            appScan: [applicationId: '3a1b2c3d-1111-4a5b-8c9d-0e1f2a3b4c5d', sastScanName: 'payhub-gateway',
                                      dastEnabled: true, dastTargetUrl: 'https://payhub-uat.testbbh.com', dastPresenceId: 'presence-7'],
                            sonar: [projectName: 'PayHub Gateway', projectKey: 'payhub-gateway'],
                            nexusIq: [application: 'payhub-gateway', scanPatterns: ['**/target/*.jar', '**/target/*.war']],
                            scm: [repositoryUrl: 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-gateway',
                                  credentialsId: 'bitbucket-http-credentials'],
                            metrics: [influxProject: 'payhub-gateway', influxEnv: 'uat'],
                            additionalConfig: [yaml: '''\
                                tests:
                                  regression:
                                    jobs:
                                      - name: PayHub regression
                                        type: local
                                        job: payhub/regression-tests
                                        timeoutMin: 60
                                deploy:
                                  openshift:
                                    project: payhub-uat
                                '''.stripIndent()]),
                    service(name: 'mobile-app', description: 'Flutter mobile application',
                            build: [tool: 'FLUTTER', sourceDir: 'mobile', autoSetup: true],
                            scm: [goldenFixEnabled: false],
                            additionalConfig: [yaml: 'flutter:\n  platform: apk\n'])])

    def "the pipeline and product configs keep the shape the DevSecOps library reads"() {
        given:
        def payments = createProduct(PAYMENTS)
        def security = createPipeline(payments.services[0].id as long, pipeline(type: 'SECURITY',
                agentLabels: ['linux-agent', 'docker'], extendedPipelineJob: 'PAYHUB/gateway-extended'))

        when:
        def pipelineConfig = api.get("/api/dso/config/$security.activeKey.value")
        def productConfig = api.get("/api/products/$payments.id/config")

        then:
        pipelineConfig.status == 200
        productConfig.status == 200
        matchesExpected('pipeline-config.yaml', pipelineConfig.body)
        matchesExpected('product-config.yaml', productConfig.body)
    }

    def "the portal shows a pipeline the same config its key gets, without marking the key as used"() {
        given:
        def mobile = createProduct(PAYMENTS + [code: uniqueCode('PREVIEW'), name: 'Preview Payments Hub',
                                               services: [PAYMENTS.services[1]]])
        def full = createPipeline(mobile.services[0].id as long, pipeline(type: 'FULL'))

        when:
        def preview = api.get("/api/pipelines/$full.id/config")

        then:
        preview.status == 200
        preview.header('Content-Type').startsWith('application/yaml')
        api.get("/api/pipelines/$full.id").json.activeKey.lastUsedAt == null

        and:
        preview.body == api.get("/api/dso/config/$full.activeKey.value").body
        api.get("/api/pipelines/$full.id").json.activeKey.lastUsedAt != null
    }

    private static boolean matchesExpected(String name, String actual) {
        def file = new File(EXPECTED_DIR, name)
        if (Boolean.getBoolean('regression.updateExpected')) {
            file.parentFile.mkdirs()
            file.text = actual
        }
        assert file.exists(): "$file is missing; create it with -Dregression.updateExpected=true"
        assert actual.normalize() == file.text.normalize()
        true
    }
}
