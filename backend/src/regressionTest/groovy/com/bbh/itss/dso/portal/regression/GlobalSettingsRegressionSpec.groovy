package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.ApiJson
import com.bbh.itss.dso.portal.support.PortalSpecification
import org.yaml.snakeyaml.Yaml

import static com.bbh.itss.dso.portal.support.ApiJson.pipeline
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service

class GlobalSettingsRegressionSpec extends PortalSpecification {

    Map original

    def setup() {
        original = api.get('/api/settings').json as Map
    }

    def cleanup() {
        def current = api.get('/api/settings').json
        if (current.version != original.version) {
            def restored = api.put('/api/settings', original + [version: current.version])
            assert restored.status == 200: restored
        }
    }

    def "the global settings start as the defaults DSOEnhanced ships with"() {
        expect:
        with(original) {
            version >= 0
            platform.jenkinsUrl == null
            platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
            platform.asocUrl == 'https://bbh.cloud.appscan.com'
            platform.sonarServerUrl == 'https://tools.bbh.com/sonar'
            platform.nexusIqServerUrl == 'https://tools.bbh.com/IQ'
            platform.nexusIqCredentialsId == 'nexusiqP'
            platform.proxyHost == 'tstproxy.bbh.com'
            platform.proxyPort == 9090
            limits == [SAST    : [maxCritical: 0, maxHigh: 0, maxMedium: 0], SCA: [maxCritical: 0, maxHigh: 0, maxMedium: 0],
                       NEXUS_IQ: [maxCritical: 0, maxHigh: 0, maxMedium: 0], DAST: [maxCritical: 0, maxHigh: 0, maxMedium: 0]]
            scans.coverageMinLine == 60
            releaseGate.scanners == ['SAST', 'SCA', 'NEXUS_IQ', 'DAST']
            serviceDefaults == [buildTool: 'GRADLE', deployTarget: 'VM', sourceDir: '.', testsMaxParallel: 20]
            deployment.urbanCodeSiteName == 'deploy.bbh.com'
            goldenFix.commitAuthorName == 'DevSecOps GoldenFix'
        }
    }

    def "a change is stored, counts as a new version and reaches every pipeline's configuration"() {
        given:
        def code = uniqueCode('GLOBAL')
        def created = createProduct(product(code: code, name: "Product $code", services: [service(name: 'gui')]))
        def full = createPipeline(created.services[0].id as long, pipeline(jenkinsJob: "DevSecOps/$code/gui-full"))
        String key = full.activeKey.value

        when:
        def changed = api.put('/api/settings', original + [
                platform: original.platform + [jenkinsUrl: 'https://jenkins.bbh.com/', sonarServerUrl: 'https://sonar.bbh.com'],
                limits  : original.limits + [SAST: [maxCritical: 0, maxHigh: 2, maxMedium: 10]],
                scans   : original.scans + [coverageMinLine: 80]])

        then:
        changed.status == 200
        changed.json.version == original.version + 1
        changed.json.platform.jenkinsUrl == 'https://jenkins.bbh.com/'
        changed.json.limits.SAST == [maxCritical: 0, maxHigh: 2, maxMedium: 10]
        api.get('/api/settings').json == changed.json

        and: 'the pipeline reads the new values, also from the database view'
        def config = new Yaml().load(api.get("/api/dso/config/$key").body) as Map
        config.platform.jenkinsUrl == 'https://jenkins.bbh.com/'
        config.defaults.sast.maxHigh == 2
        config.defaults.coverage.minLine == 80
        config.projects.gui.tools.sonar.serverUrl == 'https://sonar.bbh.com'
        def published = ApiJson.parse(libraryConfig(key).CONFIG_JSON as String)
        published.platform.jenkinsUrl == 'https://jenkins.bbh.com/'
        published.defaults.sast.maxHigh == 2

        and: 'the job path of the pipeline now links to that Jenkins'
        api.get("/api/pipelines/$full.id").json.jenkinsJobUrl == "https://jenkins.bbh.com/job/DevSecOps/job/$code/job/gui-full/"
    }

    def "a service follows the global GoldenFix default unless it sets its own"() {
        given:
        def code = uniqueCode('GOLDEN')
        def created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'follows'), service(name: 'opted-in', goldenFix: [enabled: true])]))

        when:
        def changed = api.put('/api/settings', original + [goldenFix: original.goldenFix + [enabled: false]])
        def projects = api.get("/api/products/$created.id/config?format=json").json.projects
        def defaults = api.get('/api/settings/config?format=json').json.defaults

        then:
        changed.status == 200
        created.services*.goldenFix*.enabled == [null, true]
        defaults.goldenFix.enabled == false
        !projects.follows.containsKey('goldenFix')
        projects['opted-in'].goldenFix == [enabled: true]
        jdbc.queryForList('SELECT GOLDEN_FIX_ENABLED FROM DSO_SERVICE WHERE PRODUCT_ID = ? ORDER BY DISPLAY_ORDER',
                Integer, created.id) == [null, 1]
    }

    def "the global GoldenFix default stays on when a change does not say otherwise"() {
        when:
        def changed = api.put('/api/settings', original + [goldenFix: original.goldenFix + [enabled: null]])

        then:
        changed.status == 200
        changed.json.goldenFix.enabled == true
    }

    def "a change based on an outdated version is refused"() {
        given:
        api.put('/api/settings', original + [scans: original.scans + [coverageMinLine: 70]])

        when:
        def stale = api.put('/api/settings', original + [scans: original.scans + [coverageMinLine: 75]])

        then:
        stale.status == 409
        api.get('/api/settings').json.scans.coverageMinLine == 70
    }

    def "settings the pipelines could not work with are refused with every problem"() {
        when:
        def response = api.put('/api/settings', original + [
                platform : original.platform + [proxyPort: null],
                limits   : original.limits.findAll { it.key != 'DAST' },
                goldenFix: original.goldenFix + [commitAuthorName: null, ecosystems: []]])

        then:
        response.status == 400
        response.json.errors*.field == ['platform.proxyPort', 'limits.DAST', 'goldenFix.commitAuthorName',
                                        'goldenFix.ecosystems']
        api.get('/api/settings').json.version == original.version
    }

    def "malformed settings are refused before they reach the business rules"() {
        expect:
        api.put('/api/settings', change(original)).status == 400

        where:
        change << [{ Map it -> it + [platform: null] },
                   { Map it -> it + [platform: it.platform + [jenkinsUrl: 'jenkins.bbh.com']] },
                   { Map it -> it + [platform: it.platform + [asocUrl: ' ']] },
                   { Map it -> it + [limits: it.limits + [SAST: [maxCritical: -1, maxHigh: 0, maxMedium: 0]]] },
                   { Map it -> it + [scans: it.scans + [coverageMinLine: 101]] },
                   { Map it -> it + [releaseGate: it.releaseGate + [scanners: ['SONAR']]] }]
    }
}
