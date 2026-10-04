package com.bbh.dso.portal.monitoring

import spock.lang.Specification

class GrafanaPanelsSpec extends Specification {

    def tag = new MetricsTag('CERT-gui', 'test')

    def "without a Grafana URL there are no links"() {
        given:
        def panels = new GrafanaPanels(new GrafanaProperties(url, 1, 'dso-portal-dora', 'devsecops-pipeline-dora', 'light', null))

        expect:
        !panels.configured()
        panels.links(tag, 30) == null

        where:
        url << [null, '  ']
    }

    def "the dashboard and each panel are linked with the pipeline's tags and range"() {
        given:
        def panels = new GrafanaPanels(new GrafanaProperties('https://grafana.bbh.com//', 3, 'dso-portal-dora',
                'devsecops-pipeline-dora', 'dark', null))
        def query = 'orgId=3&var-project=CERT-gui&var-env=test&from=now-90d&to=now&theme=dark'

        when:
        def links = panels.links(tag, 90)

        then:
        panels.configured()
        panels.url() == 'https://grafana.bbh.com//'
        links.dashboardUrl() == "https://grafana.bbh.com/d/dso-portal-dora/devsecops-pipeline-dora?$query"
        links.panels()*.id() == (1..8).toList()
        links.panels()*.width() == [6, 6, 4, 8, 12, 6, 6, 12]
        links.panels()[0].title() == 'Deployment frequency'
        links.panels()[0].url() == "https://grafana.bbh.com/d-solo/dso-portal-dora/devsecops-pipeline-dora?$query&panelId=1"
    }

    def "tag values are encoded in the links"() {
        given:
        def panels = new GrafanaPanels(new GrafanaProperties('http://grafana', 1, 'uid', 'slug', 'light', null))

        expect:
        panels.links(new MetricsTag('CERT gui', 'test&prod'), 7).dashboardUrl() ==
                'http://grafana/d/uid/slug?orgId=1&var-project=CERT%20gui&var-env=test%26prod&from=now-7d&to=now&theme=light'
    }

    def "configured panels replace the default ones"() {
        given:
        def custom = [new GrafanaProperties.Panel(12, 'Lead time by branch', 12)]

        expect:
        new GrafanaProperties('http://grafana', 1, 'uid', 'slug', 'light', custom).panels() == custom
        new GrafanaProperties('http://grafana', 1, 'uid', 'slug', 'light', []).panels() == GrafanaProperties.DEFAULT_PANELS
    }

    def "InfluxDB is configured with a URL"() {
        expect:
        new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', null, '365d').configured() == configured

        where:
        url                     || configured
        null                    || false
        ' '                     || false
        'http://localhost:8086' || true
    }
}
