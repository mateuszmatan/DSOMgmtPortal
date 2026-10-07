package com.bbh.itss.dso.portal.adapter.out.localmetrics

import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.support.Fixtures.pipeline
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static com.bbh.itss.dso.portal.support.Fixtures.revokedKey
import static com.bbh.itss.dso.portal.support.Fixtures.storedSettings

class DemoRunHistorySpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-07T12:00:00Z')

    LocalMetricsStore store = Mock()
    ReadMonitoringTargetsUseCase targets = Stub()
    def history = new DemoRunHistory(store, targets, Clock.fixed(NOW, ZoneOffset.UTC))

    def certScanner = product(id: 1L, code: 'CERT', services: [[name: 'gui', id: 10L], [name: 'backend-api', id: 11L]])

    def setup() {
        targets.everything() >> new MonitoringTargets([certScanner], [
                view(pipeline(id: 100L, serviceId: 10L, jenkinsJob: 'DevSecOps/CERT/gui-full')),
                view(pipeline(id: 101L, serviceId: 10L, type: PipelineType.SAST, jenkinsJob: 'DevSecOps/CERT/gui-sast',
                        keys: [revokedKey()])),
                view(pipeline(id: 102L, serviceId: 11L, jenkinsJob: 'DevSecOps/CERT/api-full'))],
                storedSettings('https://jenkins.test').platform(), [] as Set)
    }

    def "a store that holds points is left alone"() {
        when:
        history.recordOnce()

        then:
        1 * store.count() >> 12
        0 * store.save(_)
    }

    def "an empty store gets the same random history of every pipeline, and a retired one stops earlier"() {
        given:
        List<List<StoredPoint>> saved = []
        store.count() >> 0
        store.save(_) >> { arguments -> saved << arguments[0] }

        when:
        history.recordOnce()
        history.recordOnce()

        then:
        saved.size() == 2
        saved[0] == saved[1]
        def runs = saved[0].findAll { it.measurement() == 'pipeline_run' }.groupBy { it.job() }
        runs.keySet() == ['DevSecOps/CERT/gui-full', 'DevSecOps/CERT/gui-sast', 'DevSecOps/CERT/api-full'] as Set
        runs.values().flatten().every { it.time().isAfter(NOW - DemoRunHistory.HISTORY) && !it.time().isAfter(NOW) }
        runs['DevSecOps/CERT/gui-sast']*.time().max().isBefore(NOW - Duration.ofDays(9))
        runs['DevSecOps/CERT/gui-full']*.time().max().isAfter(NOW - Duration.ofDays(5))
        runs['DevSecOps/CERT/gui-full']*.tag().unique()*.project() == ['CERT-gui']
        runs['DevSecOps/CERT/gui-sast']*.tag().unique()*.project() == ['CERT-guisast']
    }

    private PipelineView view(pipeline) {
        PipelineView.of(certScanner, pipeline, 'https://jenkins.test')
    }
}
