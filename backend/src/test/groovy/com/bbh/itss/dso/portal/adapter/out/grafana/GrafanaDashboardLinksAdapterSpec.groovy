package com.bbh.itss.dso.portal.adapter.out.grafana

import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

class GrafanaDashboardLinksAdapterSpec extends Specification {

    static final String PIPELINE = 'https://grafana.bbh.com/d/adzfc54123/devsecops-pipeline-long'
    static final String SECURITY = 'https://grafana.bbh.com/d/ad2trcm/devsecops-security?orgId=2'

    def tag = new MetricsTag('CERT gui', 'test')

    def "without the pipeline dashboard there are no links"() {
        given:
        def adapter = new GrafanaDashboardLinksAdapter(new GrafanaProperties(url, SECURITY))

        expect:
        adapter.url().isEmpty()
        adapter.dashboardUrl(tag, PipelineType.SECURITY, 30).isEmpty()

        where:
        url << [null, '  ']
    }

    def "the #type pipeline links #dashboard with its project and range"() {
        given:
        def adapter = new GrafanaDashboardLinksAdapter(new GrafanaProperties(" $PIPELINE ", security))

        expect:
        adapter.url() == Optional.of(PIPELINE)
        adapter.dashboardUrl(tag, type, 90).orElseThrow() == link.toString()

        where:
        type                  | security || dashboard            | link
        PipelineType.FULL     | SECURITY || 'the pipeline one'   | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        PipelineType.EXTENDED | SECURITY || 'the pipeline one'   | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        PipelineType.SECURITY | SECURITY || 'the security one'   | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        PipelineType.SAST     | SECURITY || 'the security one'   | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        PipelineType.SAST     | ' '      || 'the pipeline one'   | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
    }
}
