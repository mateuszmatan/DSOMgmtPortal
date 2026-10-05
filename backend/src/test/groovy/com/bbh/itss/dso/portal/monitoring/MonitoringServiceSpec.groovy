package com.bbh.itss.dso.portal.monitoring

import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.catalog.ProductRepository
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import com.bbh.itss.dso.portal.monitoring.MonitoringDtos.GrafanaLinks
import com.bbh.itss.dso.portal.pipeline.PipelineRepository
import com.bbh.itss.dso.portal.pipeline.PipelineType
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.monitoring.RunResult.DISABLED
import static com.bbh.itss.dso.portal.monitoring.RunResult.FAILURE
import static com.bbh.itss.dso.portal.monitoring.RunResult.NO_DATA
import static com.bbh.itss.dso.portal.monitoring.RunResult.SUCCESS
import static com.bbh.itss.dso.portal.monitoring.RunResult.UNSTABLE
import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.service
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class MonitoringServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-04T12:00:00Z')

    ProductRepository products = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineRepository pipelines = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    PipelineMetricsRepository metrics = Mock()
    GrafanaPanels grafana = Mock()
    ManageGlobalSettingsUseCase settings = Stub() {
        current() >> storedSettings('https://jenkins.test')
    }

    @Subject
    def monitoring = new MonitoringService(products, pipelines, metrics, grafana, settings,
            Clock.fixed(NOW, ZoneOffset.UTC))

    def certScanner = product(id: 1, code: 'CERT', name: 'CertScanner', ownerTeam: 'TA')
    def gui = service(certScanner, name: 'gui', id: 10)
    def api = service(certScanner, name: 'backend-api', id: 11)
    def guiFull = pipeline(gui, id: 100, jenkinsJob: 'DevSecOps/CERT/gui-full')
    def guiSast = pipeline(gui, id: 101, type: PipelineType.SAST)
    def apiFull = pipeline(api, id: 102)
    def payments = product(id: 2, code: 'PAY', name: 'Payments Hub')
    def gatewayFull = pipeline(service(payments, name: 'gateway', id: 20), id: 200)

    def setup() {
        guiSast.revokeActiveKey('retired')
    }

    def "the status says InfluxDB and Grafana are not configured"() {
        when:
        def status = monitoring.status()

        then:
        status == new MonitoringDtos.MonitoringStatus(false, false, null, false, null)
        0 * metrics.ping()
    }

    def "the status says InfluxDB answers and where Grafana is"() {
        given:
        metrics.configured() >> true
        grafana.configured() >> true
        grafana.url() >> 'https://grafana.bbh.com'

        when:
        def status = monitoring.status()

        then:
        1 * metrics.ping()
        status == new MonitoringDtos.MonitoringStatus(true, true, null, true, 'https://grafana.bbh.com')
    }

    def "the status reports why InfluxDB cannot be read"() {
        given:
        metrics.configured() >> true
        metrics.ping() >> { throw new IllegalStateException('Connection refused') }

        when:
        def status = monitoring.status()

        then:
        !status.influxReachable()
        status.influxError() == 'InfluxDB could not be read: Connection refused'
    }

    def "the overview rates each product by its worst pipeline"() {
        given:
        def success = run('2026-10-03T10:00:00Z', SUCCESS)
        def failure = run('2026-10-04T09:00:00Z', FAILURE)
        def empty = product(id: 3, code: 'EMPTY', name: 'Empty')
        products.findAllByOrderByNameAsc() >> [certScanner, empty, payments]
        pipelines.findAllWithService() >> [guiFull, guiSast, apiFull, gatewayFull]
        metrics.configured() >> true

        when:
        def overview = monitoring.overview()

        then:
        1 * metrics.latestRuns({ it.size() == 4 }) >> [(MetricsTag.of(guiFull)): success, (MetricsTag.of(apiFull)): failure]
        overview.metricsError() == null
        overview.products()*.code == ['CERT', 'EMPTY', 'PAY']
        with(overview.products()[0]) {
            overall == FAILURE
            statusCounts == [(SUCCESS): 1, (FAILURE): 1, (DISABLED): 1]
            lastRunAt == failure.time()
            serviceCount == 2
            pipelineCount == 3
            ownerTeam == 'TA'
        }
        with(overview.products()[1]) {
            overall == NO_DATA
            statusCounts == [:]
            pipelineCount == 0
            lastRunAt == null
        }
        overview.products()[2].overall == NO_DATA
    }

    def "without InfluxDB the overview still lists every pipeline"() {
        given:
        products.findAllByOrderByNameAsc() >> [certScanner]
        pipelines.findAllWithService() >> [guiFull, guiSast, apiFull]

        when:
        def overview = monitoring.overview()

        then:
        0 * metrics.latestRuns(_)
        overview.metricsError() == MonitoringService.NOT_CONFIGURED
        overview.products()[0].statusCounts == [(NO_DATA): 2, (DISABLED): 1]
    }

    def "without any pipeline the overview reports only a missing configuration"() {
        given:
        products.findAllByOrderByNameAsc() >> [certScanner]
        metrics.configured() >> configured

        expect:
        monitoring.overview().metricsError() == error

        where:
        configured || error
        true       || null
        false      || MonitoringService.NOT_CONFIGURED
    }

    def "a failing InfluxDB is reported in short"() {
        given:
        products.findAllByOrderByNameAsc() >> [certScanner]
        pipelines.findAllWithService() >> [guiFull]
        metrics.configured() >> true
        metrics.latestRuns(_) >> { throw failure }

        expect:
        monitoring.overview().metricsError() == error

        where:
        failure                                || error
        new RuntimeException('x' * 400)        || 'InfluxDB could not be read: ' + 'x' * 300
        new IllegalStateException()            || 'InfluxDB could not be read: IllegalStateException'
    }

    def "a product shows the status and last run of each pipeline"() {
        given:
        def unstable = run('2026-10-04T09:00:00Z', UNSTABLE)
        products.findById(1L) >> Optional.of(certScanner)
        pipelines.findByProductId(1L) >> [guiFull, guiSast]
        metrics.configured() >> true
        metrics.latestRuns(_) >> [(MetricsTag.of(guiFull)): unstable]

        when:
        def product = monitoring.product(1L)

        then:
        product.code() == 'CERT'
        product.overall() == UNSTABLE
        product.pipelines()*.status() == [UNSTABLE, DISABLED]
        product.pipelines()[0].lastRun() == unstable
        product.pipelines()[0].pipeline().id() == 100
        product.pipelines()[0].pipeline().jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
        product.pipelines()[1].lastRun() == null
        product.pipelines()[1].pipeline().jenkinsJobUrl() == null
        product.metricsError() == null
    }

    def "an unknown product is not found"() {
        when:
        monitoring.product(9L)

        then:
        thrown(NotFoundException)
    }

    def "a pipeline's details show its runs, DORA metrics and Grafana panels"() {
        given:
        def tag = MetricsTag.of(guiFull)
        def newest = run('2026-10-04T09:00:00Z', SUCCESS)
        def links = new GrafanaLinks('https://grafana/d/x', [])
        pipelines.findWithServiceById(100L) >> Optional.of(guiFull)
        metrics.configured() >> true

        when:
        def details = monitoring.pipeline(100L, '30d')

        then:
        1 * metrics.recentRuns(tag, 30, 25) >> [newest, run('2026-10-03T09:00:00Z', FAILURE)]
        1 * metrics.doraPoints(tag, 30) >> [new DoraPoint(newest.time(), true, false, 3600, 600)]
        1 * grafana.links(tag, 30) >> links
        0 * metrics.latestRuns(_)
        details.pipeline().keys().size() == 1
        details.pipeline().jenkinsJobUrl() == 'https://jenkins.test/job/DevSecOps/job/CERT/job/gui-full/'
        details.status() == SUCCESS
        details.lastRun() == newest
        details.recentRuns().size() == 2
        details.dora().runs() == 1
        details.dora().daily().size() == 30
        details.dora().daily().last().date().toString() == '2026-10-04'
        details.grafana() == links
        details.metricsError() == null
    }

    def "without runs in the range the last run before it is shown"() {
        given:
        def old = run('2026-05-01T09:00:00Z', FAILURE)
        pipelines.findWithServiceById(100L) >> Optional.of(guiFull)
        metrics.configured() >> true
        metrics.recentRuns(*_) >> []
        metrics.doraPoints(*_) >> []

        when:
        def details = monitoring.pipeline(100L, '7d')

        then:
        1 * metrics.latestRuns([MetricsTag.of(guiFull)] as Set) >> [(MetricsTag.of(guiFull)): old]
        details.lastRun() == old
        details.status() == FAILURE
        details.dora().runs() == 0
    }

    def "when the runs cannot be read the DORA query is skipped"() {
        given:
        pipelines.findWithServiceById(100L) >> Optional.of(guiFull)
        metrics.configured() >> true
        metrics.recentRuns(*_) >> { throw new IllegalStateException('timeout') }

        when:
        def details = monitoring.pipeline(100L, '30d')

        then:
        0 * metrics.doraPoints(*_)
        0 * metrics.latestRuns(_)
        details.metricsError() == 'InfluxDB could not be read: timeout'
        details.status() == NO_DATA
        details.recentRuns() == []
        details.dora().runs() == 0
    }

    def "without InfluxDB a pipeline still shows its Grafana panels"() {
        given:
        def links = new GrafanaLinks('https://grafana/d/x', [])
        pipelines.findWithServiceById(101L) >> Optional.of(guiSast)
        grafana.links(_, 90) >> links

        when:
        def details = monitoring.pipeline(101L, '90d')

        then:
        details.status() == DISABLED
        details.metricsError() == MonitoringService.NOT_CONFIGURED
        details.grafana() == links
    }

    def "an unknown pipeline is not found"() {
        when:
        monitoring.pipeline(999L, '30d')

        then:
        thrown(NotFoundException)
    }

    def "the range '#range' covers #days days"() {
        expect:
        MonitoringService.rangeDays(range) == days

        where:
        range   || days
        '7d'    || 7
        ' 90d ' || 90
        '730d'  || 730
    }

    def "the range '#range' is refused"() {
        when:
        MonitoringService.rangeDays(range)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == ['range']

        where:
        range << [null, '', '0d', '30', 'd', '30h', '731d', '1000d']
    }

    private static PipelineRun run(String time, RunResult result) {
        new PipelineRun(Instant.parse(time), result, 'develop', 1, 600, null, null, null, null, null, null, null, null)
    }
}
