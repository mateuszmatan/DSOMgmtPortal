package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.bbh.itss.dso.portal.gui.support.RecordedRequest
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.bbh.itss.dso.portal.gui.support.StubResponse
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class MonitoringSpec extends GuiSpecification {

    def "the overview totals every pipeline and filters products in the browser"() {
        given:
        open('/monitoring')

        expect:
        assertThat(stat('Pipelines')).hasText('9')
        assertThat(stat('Succeeded')).hasText('6')
        assertThat(stat('Failing or unstable')).hasText('2')
        assertThat(stat('Keys invalidated')).hasText('1')
        assertThat(productCards()).hasText(['CertScanner', 'Payments Hub'] as String[])

        when:
        filter().fill('PAYMENTS')

        then:
        assertThat(productCards()).hasText(['Payments Hub'] as String[])

        when:
        filter().fill('technology')

        then:
        assertThat(productCards()).hasText(['CertScanner'] as String[])

        when:
        filter().fill('zzz')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No product matches "zzz"')
        api.requests('GET', '/api/monitoring/products').size() == 1

        when:
        filter().fill('')
        holdingText(page.locator('a.card.product'), 'Payments Hub').click()
        page.waitForURL('**/monitoring/products/2')

        then:
        assertThat(page.locator('h1')).hasText('Payments Hub')
        ownErrors().isEmpty()
    }

    def "a product's pipelines link their Jenkins jobs and the builds their runs recorded"() {
        given:
        def monitoring = StubApi.fixture('monitoring-product-2.json') as Map
        def health = monitoring.pipelines as List<Map>
        health.find { it.pipeline.serviceName == 'notifications' }.lastRun.buildUrl = null
        api.respond('GET', '/api/monitoring/products/2', monitoring)
        open('/monitoring/products/2')

        expect:
        assertThat(page.locator('tr.mat-mdc-row td.service')).hasText(health*.pipeline*.serviceName as String[])
        health.every { item ->
            def row = row(item.pipeline.id as int)
            assertThat(row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Open the Jenkins job of ${item.pipeline.serviceName}")))
                    .hasAttribute('href', item.pipeline.jenkinsJobUrl as String)
            if (item.lastRun.buildUrl) {
                assertThat(row.locator('a.build-link')).hasAttribute('href', item.lastRun.buildUrl as String)
                assertThat(row.locator('a.build-link')).hasText("#${item.lastRun.build}")
            } else {
                assertThat(row.locator('a.build-link')).hasCount(0)
                assertThat(row).containsText("#${item.lastRun.build}")
            }
            true
        }
        !health.any { it.lastRun.buildUrl?.startsWith(it.pipeline.jenkinsJobUrl as String) }

        when:
        def popup = page.waitForPopup { row(7).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName('Open the Jenkins job of ledger')).click() }

        then:
        popup.url() == 'https://jenkins.bbh.com/job/DevSecOps/job/PAYHUB/job/ledger-full/'
        page.url().endsWith('/monitoring/products/2')

        when:
        popup.close()
        row(7).locator('td.service').click()
        page.waitForURL('**/monitoring/pipelines/7')

        then:
        assertThat(page.locator('h1')).containsText('ledger')
        ownErrors().isEmpty()
    }

    def "the pipeline page asks for the selected range and keeps it in the address"() {
        given:
        api.get('/api/monitoring/pipelines/1') { RecordedRequest request ->
            def monitoring = StubApi.fixture('monitoring-pipeline-1.json') as Map
            monitoring.dora.rangeDays = (request.params().range - 'd') as int
            StubResponse.json(monitoring)
        }
        open('/monitoring/pipelines/1')

        expect:
        assertThat(radio(page.locator('mat-button-toggle-group'), '30d')).hasAttribute('aria-checked', 'true')
        assertThat(recentRunsNote()).hasText('Newest first, within the last 30 days')

        when:
        ['7d', '90d', '180d'].each { range ->
            radio(page.locator('mat-button-toggle-group'), range).click()
            page.waitForURL("**/monitoring/pipelines/1?range=$range")
            assertThat(recentRunsNote()).hasText("Newest first, within the last ${range - 'd'} days")
        }
        assertThat(page.locator('mat-progress-bar.loading')).hasCount(0)
        button('Refresh the pipeline metrics', true).click()

        then:
        assertThat(radio(page.locator('mat-button-toggle-group'), '180d')).hasAttribute('aria-checked', 'true')
        awaitRequest('GET', '/api/monitoring/pipelines/1', 5).params() == [range: '180d']
        api.requests('GET', '/api/monitoring/pipelines/1')*.params()*.range == ['30d', '7d', '90d', '180d', '180d']

        when:
        open('/monitoring/pipelines/1?range=90d')

        then:
        api.lastRequest('GET', '/api/monitoring/pipelines/1').params() == [range: '90d']
        assertThat(radio(page.locator('mat-button-toggle-group'), '90d')).hasAttribute('aria-checked', 'true')

        when:
        open('/monitoring/pipelines/1?range=1y')

        then:
        api.lastRequest('GET', '/api/monitoring/pipelines/1').params() == [range: '30d']
        ownErrors().isEmpty()
    }

    def "the pipeline page links Jenkins, Grafana and every build its runs recorded"() {
        given:
        def monitoring = StubApi.fixture('monitoring-pipeline-1.json') as Map
        def runs = monitoring.recentRuns as List<Map>
        runs[1].buildUrl = 'https://jenkins.bbh.com/job/CERTSCANNER-gui/job/full/job/feature%252Flogin/60/'
        runs[2].buildUrl = null
        api.respond('GET', '/api/monitoring/pipelines/1', monitoring)
        open('/monitoring/pipelines/1')

        expect:
        assertThat(link('Jenkins', true)).hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        assertThat(link('Open in Grafana', true)).hasAttribute('href', monitoring.grafana.dashboardUrl as String)
        assertThat(page.locator('.last-run a.build-link')).hasAttribute('href', monitoring.lastRun.buildUrl as String)
        def links = recentRuns().locator('tr.mat-mdc-row')
        assertThat(links).hasCount(runs.size())
        runs.withIndex().every { run, index ->
            def cell = links.nth(index).locator('td').nth(2)
            if (run.buildUrl) {
                assertThat(cell.locator('a.build-link')).hasAttribute('href', run.buildUrl as String)
            } else {
                assertThat(cell.locator('a')).hasCount(0)
                assertThat(cell).hasText("#${run.build}")
            }
            true
        }
        assertThat(page.locator('.grafana iframe')).hasAttribute('src', "${monitoring.grafana.dashboardUrl}&kiosk".toString())
        ownErrors().isEmpty()
    }

    def "a pipeline whose key is invalidated says so on its monitoring page"() {
        when:
        open('/monitoring/pipelines/9')

        then:
        assertThat(page.locator('.banner').first()).containsText('The key of this pipeline is invalidated, so the pipeline stops at start-up.')
        assertThat(page.locator('.page-header dso-status-chip')).containsText('Key invalidated')
        ownErrors().isEmpty()
    }

    def "without InfluxDB the pages explain that only the key state is known"() {
        given:
        api.respond('GET', '/api/monitoring/status', [influxConfigured: false, influxReachable: false, influxError: null])
        def overview = StubApi.fixture('monitoring-products.json') as Map
        overview.products.each { product -> product.overall = 'NO_DATA'; product.statusCounts = [NO_DATA: product.pipelineCount]; product.lastRunAt = null }
        api.respond('GET', '/api/monitoring/products', overview)
        def pipeline = StubApi.fixture('monitoring-pipeline-1.json') as Map
        pipeline += [status: 'NO_DATA', lastRun: null, recentRuns: [], grafana: null,
                     dora  : (pipeline.dora as Map) + [runs: 0, deployments: 0, daily: []]]
        api.respond('GET', '/api/monitoring/pipelines/1', pipeline)

        when:
        open('/monitoring')

        then:
        assertThat(page.locator('dso-metrics-banner .banner.info')).containsText('InfluxDB is not configured, so the portal shows only the state of each pipeline\'s key.')
        assertThat(stat('Pipelines')).hasText('9')
        assertThat(stat('Succeeded')).hasText('0')
        assertThat(page.locator('.product-foot .muted')).hasText(['No runs yet', 'No runs yet'] as String[])

        when:
        open('/monitoring/pipelines/1')

        then:
        assertThat(page.locator('.last-run')).containsText('No run reported yet. Runs appear once the pipeline writes its metrics to InfluxDB.')
        assertThat(page.locator('.grafana .banner.info')).containsText('Grafana is not configured.')
        assertThat(page.locator('iframe')).hasCount(0)
        ownErrors().isEmpty()
    }

    def "an unreachable InfluxDB and failed metric reads are named"() {
        given:
        api.respond('GET', '/api/monitoring/status', [influxConfigured: true, influxReachable: false, influxError: 'connection refused'])
        def overview = StubApi.fixture('monitoring-products.json') as Map
        overview.metricsError = 'query timed out after 10 seconds'
        api.respond('GET', '/api/monitoring/products', overview)

        when:
        open('/monitoring')

        then:
        assertThat(page.locator('dso-metrics-banner .banner')).hasText([
                'InfluxDB cannot be reached: connection refused',
                'Pipeline metrics could not be read, so the statuses below may be incomplete: query timed out after 10 seconds'] as String[])
        ownErrors().isEmpty()
    }

    Locator productCards() {
        page.locator('a.card.product .names strong')
    }

    Locator filter() {
        page.getByLabel('Filter products')
    }

    Locator row(int pipelineId) {
        holding(page.locator('tr.mat-mdc-row'), "a.pipeline-link[href='/monitoring/pipelines/${pipelineId}']")
    }

    Locator recentRuns() {
        holdingText(page.locator('section.card'), 'Recent runs')
    }

    Locator recentRunsNote() {
        recentRuns().locator('.card-header .muted')
    }
}
