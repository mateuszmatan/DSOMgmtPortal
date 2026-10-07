package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.gui.support.StubApi.fixture
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.LINK

class ChangeEvidenceSpec extends GuiSpecification {

    def "a product's evidence loads once when it is expanded and hides again when collapsed"() {
        given:
        open('/evidence')

        expect:
        assertThat(panel('CertScanner').locator('.counts')).hasText('2 services · 3 pipelines')
        assertThat(panel('Payments Hub').locator('.counts')).hasText('4 services · 6 pipelines')
        api.requests('GET', '/api/evidence/products/.*').isEmpty()

        when:
        header('CertScanner').click()

        then:
        assertThat(panel('CertScanner').locator('.panel-toggle')).hasText('Hide')
        assertThat(panel('CertScanner').locator('.service h3')).hasText(['gui', 'backend-api'] as String[])
        assertThat(panel('CertScanner').locator('.product-facts a')).hasAttribute('href', 'mailto:ta-team@bbh.com')
        assertThat(card('CertScanner', 'gui', 'Full').locator('a.build-link'))
                .hasAttribute('href', 'https://jenkins.bbh.com/job/CERTSCANNER-gui/job/full/62/')
        assertThat(card('CertScanner', 'gui', 'Full').getByRole(LINK, new Locator.GetByRoleOptions().setName('Jenkins job')))
                .hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        assertThat(service('CertScanner', 'backend-api').locator('.identifiers')).containsText('cert-scanner-backend.jar')
        api.requests('GET', '/api/evidence/products/1').size() == 1

        when:
        header('CertScanner').click()

        then:
        assertThat(panel('CertScanner').locator('.panel-toggle')).hasText('Show')
        assertThat(panel('CertScanner').locator('.service').first()).isHidden()

        when:
        header('CertScanner').click()

        then:
        assertThat(panel('CertScanner').locator('.service').first()).isVisible()
        api.requests('GET', '/api/evidence/products/1').size() == 1
        ownErrors().isEmpty()
    }

    def "the evidence of a pipeline is copied for ServiceNow as plain text"() {
        given:
        recordClipboard()
        open('/evidence')
        header('CertScanner').click()

        when:
        buttonIn(card('CertScanner', 'gui', 'Full'), 'Copy for ServiceNow', false).click()

        then:
        assertThat(snackBar()).containsText('Evidence copied for ServiceNow')
        copiedTexts() == [ChangeEvidenceSpec.getResource('evidence-certscanner-gui-full.txt').getText('UTF-8')]
        ownErrors().isEmpty()
    }

    def "products are found by the trimmed search term"() {
        given:
        open('/evidence')

        when:
        page.getByLabel('Find a product').fill('  pay  ')

        then:
        assertThat(page.locator('mat-expansion-panel .name')).hasText(['Payments Hub'] as String[])
        api.lastRequest('GET', '/api/products').params() == [search: 'pay']

        when:
        page.getByLabel('Find a product').fill('nothing like it')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No matching products')
        ownErrors().isEmpty()
    }

    def "evidence that cannot be read is named and loaded again on request"() {
        given:
        api.respond('GET', '/api/evidence/products/2', problem(502, 'Bad Gateway', 'InfluxDB did not answer within 10 seconds'))
        open('/evidence')

        when:
        header('Payments Hub').click()

        then:
        assertThat(panel('Payments Hub').locator('.banner')).containsText('InfluxDB did not answer within 10 seconds')

        when:
        api.respond('GET', '/api/evidence/products/2', fixture('evidence-product-2.json'))
        buttonIn(panel('Payments Hub'), 'Try again', false).click()

        then:
        assertThat(panel('Payments Hub').locator('.service h3')).hasText(['gateway', 'ledger', 'notifications', 'mobile-app'] as String[])
        api.requests('GET', '/api/evidence/products/2').size() == 2
        ownErrors().findAll { !it.contains('502') }.isEmpty()
    }

    def "runs that cannot be read are shown as not recorded with the reason"() {
        given:
        def evidence = fixture('evidence-product-1.json') as Map
        evidence.metricsError = 'InfluxDB is not reachable'
        evidence.services.each { service -> service.pipelines.each { pipeline -> pipeline.run = null; pipeline.status = 'NO_DATA' } }
        api.respond('GET', '/api/evidence/products/1', evidence)
        recordClipboard()
        open('/evidence')

        when:
        header('CertScanner').click()
        buttonIn(card('CertScanner', 'backend-api', 'Full'), 'Copy for ServiceNow', false).click()

        then:
        assertThat(panel('CertScanner').locator('.banner')).containsText('The run metrics cannot be read, so the runs show as not recorded: InfluxDB is not reachable')
        copiedTexts().size() == 1
        copiedTexts()[0].endsWith('- Status: No runs yet\n- Jenkins job: https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/backend-api-full/\n\n- Latest run: Not recorded\n')
        ownErrors().isEmpty()
    }

    Locator panel(String name) {
        holding(page.locator('mat-expansion-panel'), ".name:text-is('${name}')")
    }

    Locator header(String name) {
        panel(name).locator('mat-expansion-panel-header')
    }

    Locator service(String product, String name) {
        holding(panel(product).locator('section.service'), "h3:text-is('${name}')")
    }

    Locator card(String product, String serviceName, String type) {
        holding(service(product, serviceName).locator('dso-pipeline-evidence-card'), "h4:text-is('${type} pipeline')")
    }
}
