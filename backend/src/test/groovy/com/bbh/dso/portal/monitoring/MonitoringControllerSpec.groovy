package com.bbh.dso.portal.monitoring

import com.bbh.dso.portal.common.ApiExceptionHandler
import com.bbh.dso.portal.common.InvalidRequestException
import com.bbh.dso.portal.monitoring.MonitoringDtos.MonitoringStatus
import com.bbh.dso.portal.monitoring.MonitoringDtos.Overview
import com.bbh.dso.portal.monitoring.MonitoringDtos.PipelineMonitoring
import com.bbh.dso.portal.monitoring.MonitoringDtos.ProductHealth
import com.bbh.dso.portal.monitoring.MonitoringDtos.ProductMonitoring
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import java.time.Instant

import static com.bbh.dso.portal.support.ApiJson.parse
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class MonitoringControllerSpec extends Specification {

    MonitoringService monitoring = Mock()
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new MonitoringController(monitoring))
            .setControllerAdvice(new ApiExceptionHandler())
            .build()

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
        1 * monitoring.overview() >> new Overview([new ProductHealth(1L, 'CERT', 'CertScanner', 'TA', 2, 3,
                RunResult.FAILURE, [(RunResult.FAILURE): 1, (RunResult.SUCCESS): 2], Instant.parse('2026-10-04T09:00:00Z'))], null)
        with(parse(response.contentAsString).products[0]) {
            overall == 'FAILURE'
            statusCounts == [FAILURE: 1, SUCCESS: 2]
            lastRunAt == '2026-10-04T09:00:00Z'
        }
    }

    def "a product shows its pipelines"() {
        when:
        def response = mvc.perform(get('/api/monitoring/products/1')).andReturn().response

        then:
        1 * monitoring.product(1L) >> new ProductMonitoring(1L, 'CERT', 'CertScanner', null, 'TA', RunResult.SUCCESS, [], null)
        parse(response.contentAsString).overall == 'SUCCESS'
    }

    def "pipeline details cover 30 days unless another range is asked for"() {
        given:
        def details = new PipelineMonitoring(null, RunResult.NO_DATA, null, null, [], null, 'InfluxDB is not configured for the portal')

        when:
        def standard = mvc.perform(get('/api/monitoring/pipelines/100')).andReturn().response
        def week = mvc.perform(get('/api/monitoring/pipelines/100').param('range', '7d')).andReturn().response

        then:
        1 * monitoring.pipeline(100L, '30d') >> details
        1 * monitoring.pipeline(100L, '7d') >> details
        parse(standard.contentAsString).metricsError == 'InfluxDB is not configured for the portal'
        week.status == 200
    }

    def "an invalid range is 400"() {
        when:
        def response = mvc.perform(get('/api/monitoring/pipelines/100').param('range', '1y')).andReturn().response

        then:
        1 * monitoring.pipeline(100L, '1y') >> { throw InvalidRequestException.of('range', 'use a number of days such as 7d, 30d or 90d') }
        response.status == 400
        parse(response.contentAsString).errors[0].field == 'range'
    }
}
