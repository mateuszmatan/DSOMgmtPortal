package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.GuiSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.StubApi.fixture
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.LINK

class ChangeEvidenceSpec extends GuiSpecification {

    def "a product's evidence loads once when it is expanded and hides again when collapsed"() {
        given:
        open('/evidence')

        expect:
        assertThat(page.locator('section.department h3')).hasText(['Corporate Technology', 'Fund Services'] as String[])
        assertThat(panel('CertScanner').locator('.panel-toggle')).hasText('Show evidence')
        assertThat(panel('CertScanner').locator('.counts')).hasText('2 services · 3 pipelines')
        assertThat(panel('Payments Hub').locator('.counts')).hasText('4 services · 6 pipelines')
        api.requests('GET', '/api/evidence/products/.*').isEmpty()

        when:
        header('CertScanner').click()

        then:
        assertThat(panel('CertScanner').locator('.panel-toggle')).hasText('Hide evidence')
        assertThat(panel('CertScanner').locator('.checks dt')).hasText(['Unit tests', 'Smoke, regression and performance tests', 'SAST',
                                                                       'DAST', 'SonarQube', 'Nexus IQ', 'Golden pull request',
                                                                       'Release gate'] as String[])
        assertThat(panel('CertScanner').locator('.service h3')).hasText(['gui', 'backend-api'] as String[])
        assertThat(panel('CertScanner').locator('.product-facts a')).hasAttribute('href', 'mailto:ta-team@bbh.com')
        assertThat(card('CertScanner', 'gui', 'Full').locator('a.build-link'))
                .hasAttribute('href', 'https://jenkins.bbh.com/job/CERTSCANNER-gui/job/full/62/')
        assertThat(card('CertScanner', 'gui', 'Full').getByRole(LINK, new Locator.GetByRoleOptions().setName('Open in Jenkins')))
                .hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        assertThat(service('CertScanner', 'backend-api').locator('.identifiers')).containsText('cert-scanner-backend.jar')
        api.requests('GET', '/api/evidence/products/1').size() == 1

        when:
        header('CertScanner').click()

        then:
        assertThat(panel('CertScanner').locator('.panel-toggle')).hasText('Show evidence')
        assertThat(panel('CertScanner').locator('.service').first()).isHidden()

        when:
        header('CertScanner').click()

        then:
        assertThat(panel('CertScanner').locator('.service').first()).isVisible()
        api.requests('GET', '/api/evidence/products/1').size() == 1
        ownErrors().isEmpty()
    }

    def "the evidence of a pipeline is copied for ProTech as plain text"() {
        given:
        recordClipboard()
        open('/evidence')
        header('CertScanner').click()

        when:
        buttonIn(card('CertScanner', 'gui', 'Full'), 'Copy for ProTech', false).click()

        then:
        assertThat(snackBar()).containsText('Evidence of gui · Full pipeline copied. Paste it into the ProTech change.')
        copiedTexts() == [ChangeEvidenceSpec.getResource('evidence-certscanner-gui-full.txt').getText('UTF-8')]
        ownErrors().isEmpty()
    }

    def "the evidence of a Nexus IQ GoldenFix pipeline links the golden pull request it raised"() {
        given:
        def evidence = fixture('evidence-product-2.json') as Map
        (evidence.services as List<Map>).find { it.name == 'ledger' }.pipelines << fixture('evidence-nexus-iq-pipeline.json')
        api.respond('GET', '/api/evidence/products/2', evidence)
        recordClipboard()
        open('/evidence')

        when:
        header('Payments Hub').click()

        then:
        def nexusIq = card('Payments Hub', 'ledger', 'Nexus IQ GoldenFix')
        assertThat(nexusIq.locator('.stage'))
                .hasText(['Monitor source changes (download sources)', 'Build artifact', 'Dependencies scan (Nexus IQ)'] as String[])
        assertThat(nexusIq.locator('.golden-fix p'))
                .hasText('Pull request raised · 2 of 3 upgrades applied, 1 unresolved · GoldenFix-202610040610')
        assertThat(nexusIq.locator('.golden-fix').getByRole(LINK))
                .hasAttribute('href', 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-ledger/pull-requests/12')
        assertThat(card('Payments Hub', 'ledger', 'Full').locator('.golden-fix')).hasCount(0)

        when:
        buttonIn(nexusIq, 'Copy for ProTech', false).click()

        then:
        assertThat(snackBar()).containsText('Evidence of ledger · Nexus IQ GoldenFix pipeline copied. Paste it into the ProTech change.')
        copiedTexts().size() == 1
        copiedTexts()[0].startsWith('DevSecOps change evidence: Payments Hub (PAYHUB), ledger, Nexus IQ GoldenFix pipeline\n')
        copiedTexts()[0].contains('\n\nGoldenFix\n- Result: Pull request raised\n- Upgrades: 2 of 3 upgrades applied, 1 unresolved\n' +
                '- Pull request: GoldenFix-202610040610, https://bitbucket.bbh.com/projects/PAY/repos/payhub-ledger/pull-requests/12\n\nRelease gate\n')
        ownErrors().isEmpty()
    }

    def "products are found by the trimmed search term"() {
        given:
        open('/evidence')

        when:
        page.getByLabel('Find a product').fill('  pay  ')

        then:
        assertThat(page.locator('dso-panel .name')).hasText(['Payments Hub'] as String[])
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
        assertThat(panel('Payments Hub').locator('.banner')).containsText('The evidence could not be loaded. InfluxDB did not answer within 10 seconds')

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
        buttonIn(card('CertScanner', 'backend-api', 'Full'), 'Copy for ProTech', false).click()

        then:
        assertThat(panel('CertScanner').locator('.banner')).containsText('The run results could not be loaded, so the runs below show as not recorded (InfluxDB is not reachable).')
        copiedTexts().size() == 1
        copiedTexts()[0].endsWith('- Status: No runs yet\n- Jenkins job: https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/backend-api-full/\n\n- Latest run: Not recorded\n')
        ownErrors().isEmpty()
    }

    Locator panel(String name) {
        holding(page.locator('dso-panel'), ".name:text-is('${name}')")
    }

    Locator header(String name) {
        panel(name).locator('.accordion-button')
    }

    Locator service(String product, String name) {
        holding(panel(product).locator('section.service'), "h3:text-is('${name}')")
    }

    Locator card(String product, String serviceName, String type) {
        holding(service(product, serviceName).locator('dso-pipeline-evidence-card'), "h4:text-is('${type} pipeline')")
    }
}
