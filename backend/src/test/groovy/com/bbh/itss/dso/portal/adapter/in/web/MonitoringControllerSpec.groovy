package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringOverview
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringStatus
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineHealth
import com.bbh.itss.dso.portal.application.monitoring.port.in.PipelineMonitoring
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductHealth
import com.bbh.itss.dso.portal.application.monitoring.port.in.ProductMonitoring
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.monitoring.DoraCalculator
import com.bbh.itss.dso.portal.domain.monitoring.DoraPoint
import com.bbh.itss.dso.portal.domain.monitoring.PipelineRun
import com.bbh.itss.dso.portal.domain.monitoring.RunResult
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class MonitoringControllerSpec extends Specification {

    static final Instant FINISHED = Instant.parse('2026-10-04T09:00:00Z')

    MonitorPipelinesUseCase monitoring = Mock()
    MockMvc mvc = WebMvc.of(new MonitoringController(monitoring))

    Product certScanner = product(id: 1L, code: 'CERT', name: 'CertScanner', description: 'Scans certificates',
            ownerTeam: 'TA', services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])
    PipelineView guiFull = PipelineView.of(certScanner,
            pipeline(id: 100L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full'), 'https://jenkins.test')
    PipelineRun run = new PipelineRun(FINISHED, RunResult.SUCCESS, 'develop', 42L, 600L, 'a1b2c3d',
            'DevSecOps/CERT/gui-full', 12L, 11L, 1L, 0L, 0L, 0L)

    def "the status tells the UI what is configured"() {
        when:
        def response = mvc.perform(get('/api/monitoring/status')).andReturn().response

        then:
        1 * monitoring.status() >> new MonitoringStatus(true, false, 'down', true, 'https://grafana')
        parse(response.contentAsString) == [influxConfigured: true, influxReachable: false, influxError: 'down',
                                            grafanaConfigured: true, grafanaUrl: 'https://grafana']
    }

    def "the overview lists the products with their status counts"() {
        when:
        def response = mvc.perform(get('/api/monitoring/products')).andReturn().response

        then:
        1 * monitoring.overview() >> new MonitoringOverview([new ProductHealth(certScanner, 3, RunResult.FAILURE,
                [(RunResult.FAILURE): 1, (RunResult.SUCCESS): 2], FINISHED)], null)
        with(parse(response.contentAsString)) {
            keySet() == ['products', 'metricsError'] as Set
            products[0] == [productId    : 1, code: 'CERT', name: 'CertScanner', ownerTeam: 'TA', serviceCount: 2,
                            pipelineCount: 3, overall: 'FAILURE', statusCounts: [SUCCESS: 2, FAILURE: 1],
                            lastRunAt    : '2026-10-04T09:00:00Z']
        }
    }

    def "a product shows its pipelines with their status and last run"() {
        when:
        def response = mvc.perform(get('/api/monitoring/products/1')).andReturn().response

        then:
        1 * monitoring.product(1L) >> new ProductMonitoring(certScanner, RunResult.SUCCESS,
                [new PipelineHealth(guiFull, RunResult.SUCCESS, run)], 'slow')
        with(parse(response.contentAsString)) {
            keySet() == ['productId', 'code', 'name', 'description', 'ownerTeam', 'overall', 'pipelines',
                         'metricsError'] as Set
            productId == 1
            description == 'Scans certificates'
            overall == 'SUCCESS'
            metricsError == 'slow'
            pipelines[0].keySet() == ['pipeline', 'status', 'lastRun'] as Set
            pipelines[0].pipeline.id == 100
            pipelines[0].pipeline.keys == []
            pipelines[0].pipeline.activeKey.value == null
            pipelines[0].pipeline.activeKey.hint == '0f8fad5b\u2026950e'
            pipelines[0].pipeline.activeKey.status == 'ACTIVE'
            pipelines[0].pipeline.jenkinsJobUrl == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
            pipelines[0].lastRun == [time       : '2026-10-04T09:00:00Z', result: 'SUCCESS', branch: 'develop', build: 42,
                                     durationSeconds: 600, commit: 'a1b2c3d', job: 'DevSecOps/CERT/gui-full',
                                     buildUrl   : 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/42/',
                                     stagesTotal: 12, passed: 11, warned: 1, failed: 0, blocked: 0, skipped: 0]
        }
    }

    def "pipeline details cover 30 days unless another range is asked for"() {
        given:
        def summary = DoraCalculator.summarize([new DoraPoint(FINISHED, true, false, 3600, 600)], 7, FINISHED)
        def details = new PipelineMonitoring(guiFull, RunResult.SUCCESS, run, summary, [run], 'https://grafana/d/x', null)

        when:
        def standard = mvc.perform(get('/api/monitoring/pipelines/100')).andReturn().response
        def week = mvc.perform(get('/api/monitoring/pipelines/100').param('range', '7d')).andReturn().response

        then:
        1 * monitoring.pipeline(100L, '30d') >> details
        1 * monitoring.pipeline(100L, '7d') >> details
        week.status == 200
        with(parse(standard.contentAsString)) {
            keySet() == ['pipeline', 'status', 'lastRun', 'dora', 'recentRuns', 'grafana', 'metricsError'] as Set
            pipeline.keys == []
            pipeline.activeKey.value == null
            pipeline.activeKey.hint == '0f8fad5b\u2026950e'
            status == 'SUCCESS'
            lastRun.build == 42
            recentRuns*.build == [42]
            grafana == [dashboardUrl: 'https://grafana/d/x']
            dora.keySet() == ['rangeDays', 'runs', 'deployments', 'deploymentsPerWeek', 'deploymentFrequencyLevel',
                              'leadTimeMedianSeconds', 'leadTimeLevel', 'changeFailureRatePercent',
                              'changeFailureRateLevel', 'meanTimeToRestoreSeconds', 'timeToRestoreLevel', 'restores',
                              'failingSince', 'averageDurationSeconds', 'daily'] as Set
            dora.rangeDays == 7
            dora.deploymentFrequencyLevel == 'HIGH'
            dora.daily.last() == [date: '2026-10-04', runs: 1, failures: 0, deployments: 1]
        }
    }

    def "without InfluxDB and Grafana the details carry the reason and no dashboard"() {
        given:
        def details = new PipelineMonitoring(guiFull, RunResult.NO_DATA, null,
                DoraCalculator.summarize([], 30, FINISHED), [], null, 'InfluxDB is not configured for the portal')

        when:
        def response = mvc.perform(get('/api/monitoring/pipelines/100')).andReturn().response

        then:
        1 * monitoring.pipeline(100L, '30d') >> details
        with(parse(response.contentAsString)) {
            metricsError == 'InfluxDB is not configured for the portal'
            lastRun == null
            grafana == null
            recentRuns == []
        }
    }

    def "an invalid range is 400"() {
        when:
        def response = mvc.perform(get('/api/monitoring/pipelines/100').param('range', '1y')).andReturn().response

        then:
        1 * monitoring.pipeline(100L, '1y') >> {
            throw InvalidRequestException.of('range', 'use a number of days such as 7d, 30d or 90d')
        }
        response.status == 400
        parse(response.contentAsString).errors[0].field == 'range'
    }

    def "an unknown product is 404"() {
        when:
        def response = mvc.perform(get('/api/monitoring/products/9')).andReturn().response

        then:
        1 * monitoring.product(9L) >> { throw NotFoundException.of('Product', 9L) }
        response.status == 404
    }
}
