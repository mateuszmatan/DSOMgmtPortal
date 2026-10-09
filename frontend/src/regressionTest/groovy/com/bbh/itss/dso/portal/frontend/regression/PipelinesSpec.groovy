package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.GuiSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class PipelinesSpec extends GuiSpecification {

    static final String GUI_FULL_KEY = '7b62170e-5c42-4bec-8cbc-035977e3299e'
    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'

    def "a developer picks the department and filters and sorts its pipelines in the table header"() {
        when:
        open('/monitoring')
        menuLink('Pipelines').click()
        page.waitForURL('**/pipelines')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Pipelines')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('The pipelines of your department with their keys, settings and last runs')
        assertThat(page.locator('.empty-state h3')).hasText('Choose your department to see its pipelines.')
        assertThat(page.locator('.dso-menu')).hasCount(0)
        api.requests('GET', '/api/pipelines').isEmpty()

        when:
        choose(page.locator('.toolbar'), 'Your department', 'Fund Services')

        then:
        assertThat(column('service')).hasText(['gateway', 'gateway', 'gateway', 'ledger', 'notifications', 'mobile-app'] as String[])
        awaitRequest('GET', '/api/pipelines').params() == [departmentId: '5']
        assertThat(gridHeaders()).hasText(['Service', 'Product', 'Type', 'Jenkins job', 'Key', 'Last run', 'Ran', ''] as String[])
        assertThat(column('key')).hasText(['50b4ada7…108f', '9e9f17e8…4790', '5a07b656…5627', 'fa529f98…a726',
                                           '26b4c145…64e4', 'Invalidated'] as String[])
        assertThat(column('status')).hasText(['Success', 'Success', 'Success', 'Unstable', 'Unstable', 'Key invalidated'] as String[])
        assertThat(page.locator('.toolbar .shown')).hasText('6 of 6 pipelines')
        assertThat(link('gateway').first()).hasAttribute('href', '/pipelines/6')
        !page.locator('main').textContent().contains('5a07b656-')

        when:
        gridFilter('service').fill('gate')

        then:
        assertThat(column('type')).hasText(['Extended', 'Full', 'Security'] as String[])
        assertThat(page.locator('.toolbar .shown')).hasText('3 of 6 pipelines')

        when:
        gridFilter('service').fill('')
        choose(page.locator('.ag-floating-filter'), 'Filter by key', 'Invalidated')

        then:
        assertThat(column('service')).hasText(['mobile-app'] as String[])

        when:
        choose(page.locator('.ag-floating-filter'), 'Filter by key', 'All')
        choose(page.locator('.ag-floating-filter'), 'Filter by last run', 'Unstable')
        gridFilter('Jenkins job').fill('ledger')

        then:
        assertThat(column('service')).hasText(['ledger'] as String[])

        when:
        gridFilter('product').fill('certscanner')

        then:
        assertThat(page.locator('.dso-grid-empty')).hasText('No pipeline matches the filters.')

        when:
        gridFilter('product').fill('')
        gridFilter('Jenkins job').fill('')
        choose(page.locator('.ag-floating-filter'), 'Filter by last run', 'All')
        sortBy('type')

        then:
        assertThat(column('type')).hasText(['Full', 'Full', 'Full', 'Security', 'Extended', 'SAST scanning'] as String[])

        when:
        sortBy('type')

        then:
        assertThat(column('type').first()).hasText('SAST scanning')

        when:
        open('/pipelines')

        then:
        assertThat(select(page.locator('.toolbar'), 'Your department').locator('option:checked')).hasText('Fund Services')
        assertThat(column('service')).hasCount(6)

        when:
        choose(page.locator('.toolbar'), 'Your department', 'Custody')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No DevSecOps pipeline in Custody yet')
        assertThat(page.locator('.empty-state a')).hasAttribute('href', '/self-service')
        ownErrors().isEmpty()
    }

    def "a row opens the page of its pipeline with its key, settings, Jenkinsfile and recent runs"() {
        given:
        page.addInitScript("localStorage.setItem('dso.beadle.department', '3')")
        recordClipboard()

        when:
        open('/pipelines')
        column('service').first().click()
        page.waitForURL('**/pipelines/1')

        then:
        assertThat(page.locator('.breadcrumb a, .breadcrumb span:not(.sep)'))
                .hasText(['DevSecOps Pipelines', 'CertScanner', 'gui · Full'] as String[])
        assertThat(page.locator('h1')).hasText('gui · Full pipeline')
        assertThat(page.locator('.title .chip')).hasText('Success')
        assertThat(page.locator('.key-value')).hasText('7b62170e…299e')
        assertThat(pair('Agents')).hasText('linux-agent')
        assertThat(pair('Jenkins job')).hasText('DevSecOps/CERTSCANNER/gui-full')
        assertThat(page.locator('pre.code-block')).containsText("devSecOpsPipeline(pipelineKey: '${GUI_FULL_KEY}')")
        assertThat(gridRows()).hasCount(5)
        assertThat(link('Metrics', true)).hasAttribute('href', '/monitoring/pipelines/1')
        assertThat(link('Product', true)).hasAttribute('href', '/admin/products/1')
        assertThat(link('Jenkins', true)).hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')

        when:
        button('Show', true).click()

        then:
        assertThat(page.locator('.key-value')).hasText(GUI_FULL_KEY)

        when:
        button('Copy', true).first().click()

        then:
        assertThat(snackBar()).containsText('Key copied to the clipboard')
        copiedTexts() == [GUI_FULL_KEY]
        ownErrors().isEmpty()
    }

    def "Edit saves the settings of a pipeline and the list keeps its masked key"() {
        given:
        def saved = fixture('pipeline-1.json') as Map
        saved.agentLabels = ['linux-agent', 'docker']
        saved.description = 'Release build'
        api.on('PUT', '/api/pipelines/1') { saved }

        when:
        open('/pipelines/1')
        button('Edit', true).click()
        input(dialog(), 'Jenkins agent labels').fill('linux-agent, docker')
        input(dialog(), 'Description').fill('Release build')
        dialogButton('Save').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Pipeline settings saved')
        assertThat(pair('Agents')).hasText('linux-agent, docker')
        assertThat(pair('Description')).hasText('Release build')
        with(awaitRequest('PUT', '/api/pipelines/1').json()) {
            agentLabels == ['linux-agent', 'docker']
            description == 'Release build'
            type == 'FULL'
        }

        when:
        page.addInitScript("localStorage.setItem('dso.beadle.department', '3')")
        open('/pipelines')
        buttonIn(page.locator('dso-grid'), 'Edit the Full pipeline of gui').click()
        input(dialog(), 'Description').fill('Release build')
        dialogButton('Save').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(column('key').first()).hasText('7b62170e…299e')
        api.requests('PUT', '/api/pipelines/1').size() == 2
        ownErrors().isEmpty()
    }

    def "an invalidated key is regenerated on the page of its pipeline"() {
        given:
        api.respond('POST', '/api/pipelines/9/keys', fixture('pipeline-9-regenerated.json'))

        when:
        open('/pipelines/9')

        then:
        assertThat(page.locator('.banner.danger')).containsText('The key is invalidated')
        assertThat(page.locator('pre.code-block')).containsText('<issue a new key first>')

        when:
        button('Regenerate key', true).click()

        then:
        assertThat(page.locator('.key-value')).hasText(REGENERATED_KEY)
        assertThat(page.locator('.banner.danger')).hasCount(0)
        assertThat(page.locator('pre.code-block')).containsText(REGENERATED_KEY)
        assertThat(snackBar()).containsText('SAST scanning pipeline of mobile-app has a new key')
        api.requests('POST', '/api/pipelines/9/keys').size() == 1
        ownErrors().isEmpty()
    }

    def "an unknown pipeline leads back to the list"() {
        when:
        open('/pipelines/99')

        then:
        assertThat(page.locator('.banner')).hasText('Pipeline 99 does not exist')

        when:
        link('Back to pipelines', true).click()

        then:
        page.waitForURL('**/pipelines')
        assertThat(page.locator('h1')).hasText('DevSecOps Pipelines')
    }

    Locator column(String name) {
        gridCells(page.locator('body'), name)
    }

    Locator pair(String label) {
        holding(page.locator('.pairs > div'), "dt:text-is('${label}')").locator('dd')
    }
}
