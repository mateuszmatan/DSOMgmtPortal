package com.bbh.dso.portal.regression

import com.bbh.dso.portal.support.PortalSpecification

import java.time.Duration
import java.time.Instant

import static com.bbh.dso.portal.support.ApiJson.pipeline
import static com.bbh.dso.portal.support.ApiJson.product
import static com.bbh.dso.portal.support.ApiJson.service

/**
 * The monitoring pages end to end against an InfluxDB holding runs the way the DevSecOps library writes them.
 */
class MonitoringRegressionSpec extends PortalSpecification {

    String code
    Map monitored
    Map guiFull
    Map guiSast
    Map apiFull

    def setup() {
        influx.reset()
        code = uniqueCode('MON')
        monitored = createProduct(product(code: code, name: "Monitored $code", services: [service(name: 'gui'), service(name: 'api')]))
        guiFull = createPipeline(monitored.services[0].id as long)
        guiSast = createPipeline(monitored.services[0].id as long, pipeline(type: 'SAST'))
        apiFull = createPipeline(monitored.services[1].id as long)
        api.post("/api/pipelines/$guiSast.id/keys/revoke", [reason: 'paused'])

        def now = Instant.now()
        influx.addRun(project: "$code-gui", time: now - Duration.ofDays(3), result: 'FAILURE', leadTimeSeconds: 7200)
        influx.addRun(project: "$code-gui", time: now - Duration.ofDays(2), result: 'SUCCESS', leadTimeSeconds: 3600)
        influx.addRun(project: "$code-gui", time: now - Duration.ofHours(1), result: 'SUCCESS', leadTimeSeconds: 1800, build: 42)
        influx.addRun(project: "$code-api", time: now - Duration.ofHours(2), result: 'FAILURE')
    }

    def cleanup() {
        influx.reset()
    }

    def "the status shows InfluxDB is reachable and where Grafana is"() {
        when:
        def status = api.get('/api/monitoring/status').json

        then:
        status == [influxConfigured: true, influxReachable: true, influxError: null, grafanaConfigured: true,
                   grafanaUrl: 'http://grafana.test']
        influx.requests.last().authorization == 'Token test-token'
    }

    def "the overview rates the product by its worst pipeline"() {
        when:
        def overview = api.get('/api/monitoring/products').json
        def health = overview.products.find { it.code == code }

        then:
        overview.metricsError == null
        health.overall == 'FAILURE'
        health.statusCounts == [SUCCESS: 1, FAILURE: 1, DISABLED: 1]
        health.pipelineCount == 3
        health.serviceCount == 2
        health.lastRunAt != null
    }

    def "a product shows the status and last run of each pipeline"() {
        when:
        def monitoring = api.get("/api/monitoring/products/$monitored.id").json

        then:
        monitoring.overall == 'FAILURE'
        monitoring.pipelines*.pipeline*.id == [guiFull.id, guiSast.id, apiFull.id]
        monitoring.pipelines*.status == ['SUCCESS', 'DISABLED', 'FAILURE']
        monitoring.pipelines[0].lastRun.build == 42
        monitoring.pipelines[1].lastRun == null
    }

    def "a pipeline's details hold its runs, DORA metrics and Grafana panels"() {
        when:
        def details = api.get("/api/monitoring/pipelines/$guiFull.id?range=30d").json

        then:
        details.metricsError == null
        details.status == 'SUCCESS'
        details.recentRuns*.result == ['SUCCESS', 'SUCCESS', 'FAILURE']
        details.pipeline.keys.size() == 1
        with(details.dora) {
            runs == 3
            deployments == 2
            Math.abs(changeFailureRatePercent - 33.333) < 0.01
            changeFailureRateLevel == 'LOW'
            restores == 1
            meanTimeToRestoreSeconds == 86_400
            timeToRestoreLevel == 'MEDIUM'
            leadTimeMedianSeconds == 3600
            failingSince == null
            daily.size() == 30
        }
        details.grafana.panels.size() == 8
        details.grafana.dashboardUrl.contains("var-project=$code-gui&var-env=test&from=now-30d")
    }

    def "a pipeline without runs in the range shows the last one before it"() {
        given:
        def apiSast = createPipeline(monitored.services[1].id as long, pipeline(type: 'SAST'))
        influx.addRun(project: "$code-apisast", variant: 'sast', time: Instant.now() - Duration.ofDays(60), result: 'UNSTABLE')

        when:
        def details = api.get("/api/monitoring/pipelines/$apiSast.id?range=7d").json

        then:
        details.recentRuns == []
        details.lastRun.result == 'UNSTABLE'
        details.status == 'UNSTABLE'
        details.dora.runs == 0
    }

    def "when InfluxDB fails the pages still list every pipeline"() {
        given:
        influx.failWith(500)

        when:
        def status = api.get('/api/monitoring/status').json
        def overview = api.get('/api/monitoring/products').json
        def details = api.get("/api/monitoring/pipelines/$guiFull.id").json

        then:
        !status.influxReachable
        status.influxError.startsWith('InfluxDB could not be read: 500')
        overview.metricsError.startsWith('InfluxDB could not be read')
        overview.products.find { it.code == code }.statusCounts == [NO_DATA: 2, DISABLED: 1]
        details.status == 'NO_DATA'
        details.metricsError.startsWith('InfluxDB could not be read')
        details.grafana.panels.size() == 8
    }

    def "a range that is not a number of days is refused"() {
        expect:
        api.get("/api/monitoring/pipelines/$guiFull.id?range=1y").status == 400
        api.get('/api/monitoring/pipelines/999999').status == 404
    }
}
