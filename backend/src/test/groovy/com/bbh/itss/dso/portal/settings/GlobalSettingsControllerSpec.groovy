package com.bbh.itss.dso.portal.settings

import com.bbh.itss.dso.portal.common.ApiExceptionHandler
import com.bbh.itss.dso.portal.common.InvalidRequestException
import org.springframework.boot.ApplicationArguments
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class GlobalSettingsControllerSpec extends Specification {

    def bbh = GlobalSettingsValues.bbhDefaults()
    def current = new GlobalSettingsResponse(4, Instant.parse('2026-10-04T12:00:00Z'), bbh.platform(), bbh.deployment(),
            bbh.limits(), bbh.scans(), bbh.releaseGate(), bbh.serviceDefaults(), bbh.goldenFix())

    GlobalSettingsService settings = Mock() {
        get() >> current
    }
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new GlobalSettingsController(settings))
            .setControllerAdvice(new ApiExceptionHandler())
            .build()

    def "the settings are read with their version"() {
        when:
        def response = mvc.perform(get('/api/settings')).andReturn().response

        then:
        1 * settings.get() >> current
        response.status == 200
        with(parse(response.contentAsString)) {
            version == 4
            updatedAt == '2026-10-04T12:00:00Z'
            platform.jenkinsLibrary == 'DevSecOpsJenkinsLibrary'
            platform.proxyPort == 9090
            deployment.rdHost == 'rdltaapps1.testbbh.com'
            limits.keySet() as List == ['SAST', 'SCA', 'NEXUS_IQ', 'DAST']
            limits.NEXUS_IQ == [maxCritical: 0, maxHigh: 0, maxMedium: 0]
            scans.coverageMinLine == 60
            releaseGate == [scanners: ['SAST', 'SCA', 'NEXUS_IQ', 'DAST'], requireCoverage: true,
                            stateFile: 'release-gate.json']
            serviceDefaults == [buildTool: 'GRADLE', deployTarget: 'VM', sourceDir: '.', testsMaxParallel: 20]
            goldenFix.ecosystems == ['maven', 'npm', 'pypi']
        }
    }

    def "the settings read are accepted back with a change"() {
        given:
        Map body = readBack()
        body.platform.jenkinsUrl = 'https://jenkins.bbh.com'
        body.limits.SAST = [maxCritical: 0, maxHigh: 2, maxMedium: 10]

        when:
        def response = mvc.perform(put('/api/settings').contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andReturn().response

        then:
        1 * settings.update({ GlobalSettingsRequest r ->
            r.version() == 4L &&
                    r.values() == new GlobalSettingsValues(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'),
                    bbh.deployment(), bbh.limits() + [(Scanner.SAST): new SeverityLimits(0, 2, 10)], bbh.scans(),
                    bbh.releaseGate(), bbh.serviceDefaults(), bbh.goldenFix())
        }) >> current
        response.status == 200
        parse(response.contentAsString).version == 4
    }

    def "nested values are validated: #field #message"() {
        given:
        Map body = readBack()
        change(body)

        when:
        def response = mvc.perform(put('/api/settings').contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andReturn().response

        then:
        0 * settings.update(_)
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Validation failed'
            errors == [[field: field, message: message]]
        }

        where:
        field                      | message                                            | change
        'platform'                 | 'must not be null'                                 | { it.platform = null }
        'platform.asocUrl'         | 'must be an http or https URL'                     | { it.platform.asocUrl = 'ftp://appscan.bbh.com' }
        'platform.proxyPort'       | 'must be less than or equal to 65535'              | { it.platform.proxyPort = 70000 }
        'deployment.qcHost'        | 'must be a host name'                              | { it.deployment.qcHost = 'qc host' }
        'limits[DAST].maxHigh'     | 'must be greater than or equal to 0'               | { it.limits.DAST.maxHigh = -1 }
        'scans.coverageMinLine'    | 'must be less than or equal to 100'                | { it.scans.coverageMinLine = 101 }
        'releaseGate.stateFile'    | 'must be a file name such as release-gate.json'    | { it.releaseGate.stateFile = 'gate state.json' }
        'serviceDefaults.buildTool'| 'must not be null'                                 | { it.serviceDefaults.buildTool = null }
        'goldenFix.ecosystems[0]'  | 'must be maven, npm, pypi or pub'                  | { it.goldenFix.ecosystems = ['gradle'] }
    }

    def "several invalid values are counted"() {
        given:
        Map body = readBack()
        body.platform.sonarServerUrl = null
        body.scans.sastPollIntervalSeconds = 0

        when:
        def response = mvc.perform(put('/api/settings').contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andReturn().response

        then:
        0 * settings.update(_)
        response.status == 400
        with(parse(response.contentAsString)) {
            detail == '2 fields are invalid'
            errors*.field.sort() == ['platform.sonarServerUrl', 'scans.sastPollIntervalSeconds']
        }
    }

    def "an empty scanner in the release gate is refused"() {
        given:
        Map body = readBack()
        body.releaseGate.scanners = ['SAST', null]

        when:
        def response = mvc.perform(put('/api/settings').contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andReturn().response

        then:
        0 * settings.update(_)
        response.status == 400
        parse(response.contentAsString).status == 400
    }

    def "a business rule violation is 400 with its field"() {
        when:
        def response = mvc.perform(put('/api/settings').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(readBack()))).andReturn().response

        then:
        1 * settings.update(_) >> { throw InvalidRequestException.of('limits.DAST', 'set the limits of every scanner') }
        response.status == 400
        parse(response.contentAsString).errors == [[field: 'limits.DAST', message: 'set the limits of every scanner']]
    }

    def "a change made at an older version is 409"() {
        when:
        def response = mvc.perform(put('/api/settings').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(readBack()))).andReturn().response

        then:
        1 * settings.update(_) >> { throw new ObjectOptimisticLockingFailureException(GlobalSettings, GlobalSettings.ID) }
        response.status == 409
        parse(response.contentAsString).detail ==
                'The record was changed by someone else in the meantime. Reload it and apply your change again.'
    }

    def "the settings exist before anything else runs at start-up"() {
        given:
        def initializer = new GlobalSettingsInitializer(settings)

        when:
        initializer.run(Stub(ApplicationArguments))

        then:
        1 * settings.ensureExists() >> new GlobalSettings(bbh)
    }

    /** The body the portal sends back: what GET returned, without the modification time. */
    private Map readBack() {
        Map read = parse(mvc.perform(get('/api/settings')).andReturn().response.contentAsString) as Map
        read.remove('updatedAt')
        read
    }
}
