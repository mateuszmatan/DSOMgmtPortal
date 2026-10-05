package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class ProductCatalogueSpec extends ProductPageSpecification {

    def "the catalogue lists every product with its services and the state of its pipeline keys"() {
        when:
        open('/products')

        then:
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        assertThat(page.locator('.toolbar .count')).hasText('2 products')
        assertThat(row('Payments Hub').locator('td').nth(2)).hasText('4')
        assertThat(row('Payments Hub').locator('.pipelines strong')).hasText('6')
        assertThat(row('Payments Hub').locator('.pipelines .muted')).hasText('· 5 active')
        assertThat(row('Payments Hub').locator('.pipelines .revoked')).hasText('· 1 invalidated')
        assertThat(row('CertScanner').locator('.pipelines .muted')).hasText('· 3 active')
        assertThat(row('CertScanner').locator('.pipelines .revoked')).hasCount(0)
        api.requests('GET', '/api/products')*.params() == [[:]]
        ownErrors().isEmpty()
    }

    def "searching asks the API for the trimmed term and says when nothing matches"() {
        given:
        open('/products')

        when:
        search().fill('  Payments  ')

        then:
        assertThat(names()).hasText(['Payments Hub'] as String[])
        assertThat(page.locator('.toolbar .count')).hasText('1 product')
        api.lastRequest('GET', '/api/products').params() == [search: 'Payments']

        when:
        search().fill('ledger-x')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No product matches "ledger-x"')
        assertThat(page.locator('.empty-state')).containsText('Try another name, code or team.')

        when:
        search().fill('')

        then:
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        api.lastRequest('GET', '/api/products').params() == [:]
        ownErrors().isEmpty()
    }

    def "a product opens from its row or its name and the breadcrumb leads back"() {
        given:
        open('/products')

        when:
        row('Payments Hub').locator('td').nth(1).click()
        page.waitForURL('**/products/2')

        then:
        assertThat(page.locator('h1')).hasText('Payments Hub')
        assertThat(page.locator('.page-header .code')).hasText('PAYHUB')
        assertThat(serviceCard('mobile-app').locator('.tag').first()).hasText('Flutter')
        assertThat(serviceCard('gateway').locator('.repository a'))
                .hasAttribute('href', 'https://bitbucket.bbh.com/projects/PAY/repos/payhub-gateway')
        assertThat(stat('Services')).hasText('4')
        assertThat(stat('Pipelines')).hasText('6')
        assertThat(stat('Active keys')).hasText('5')
        assertThat(stat('Invalidated keys')).hasText('1')

        when:
        page.locator('.breadcrumb a').click()
        page.waitForURL('**/products')
        row('CertScanner').locator('a.name').click()
        page.waitForURL('**/products/1')

        then:
        assertThat(page.locator('h1')).hasText('CertScanner')
        assertThat(pipelineTypes('gui')).hasText(['Full pipeline', 'SAST scanning pipeline'] as String[])
        assertThat(link('Monitoring', true)).hasAttribute('href', '/monitoring/products/1')
        assertThat(pipelineRow('gui', 'Full').getByRole(AriaRole.LINK,
                new Locator.GetByRoleOptions().setName('Jenkins').setExact(true)))
                .hasAttribute('href', 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-full/')
        ownErrors().isEmpty()
    }

    Locator names() {
        page.locator('td a.name')
    }

    Locator row(String name) {
        page.locator('tr.mat-mdc-row').filter(new Locator.FilterOptions().setHas(page.locator("a.name:text-is('${name}')")))
    }

    Locator search() {
        page.getByLabel('Search products')
    }
}
