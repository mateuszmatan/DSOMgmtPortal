package com.bbh.itss.dso.portal.adapter.out.grafana

import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST

class GrafanaDashboardLinksAdapterSpec extends Specification {

    static final String PIPELINE = 'https://grafana.bbh.com/d/adzfc54123/devsecops-pipeline-long'
    static final String SECURITY = 'https://grafana.bbh.com/d/ad2trcm/devsecops-security?orgId=2'

    def tag = new MetricsTag('CERT gui', 'test')

    def "the #type pipeline links #dashboard with its project and range"() {
        given:
        def adapter = new GrafanaDashboardLinksAdapter(new GrafanaProperties(" $PIPELINE ", security))

        expect:
        adapter.url() == Optional.of(PIPELINE)
        adapter.dashboardUrl(tag, type, 90).orElseThrow() == link.toString()
        [null, '  '].every {
            new GrafanaDashboardLinksAdapter(new GrafanaProperties(it, SECURITY)).dashboardUrl(tag, type, 30)
                    .isPresent() == securityAlone
        }
        new GrafanaDashboardLinksAdapter(new GrafanaProperties(null, SECURITY)).url().isEmpty()

        where:
        type                  | security || dashboard          | securityAlone | link
        FULL                  | SECURITY || 'the pipeline one' | false         | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        EXTENDED              | SECURITY || 'the pipeline one' | false         | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        PipelineType.SECURITY | SECURITY || 'the security one' | true          | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        SAST                  | SECURITY || 'the security one' | true          | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        SAST                  | ' '      || 'the pipeline one' | true          | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
    }
}
