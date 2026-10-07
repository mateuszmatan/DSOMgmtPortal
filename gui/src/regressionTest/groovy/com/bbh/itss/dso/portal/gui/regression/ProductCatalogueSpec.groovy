package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.StubApi
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class ProductCatalogueSpec extends ProductPageSpecification {

    def "the catalogue lists the products by department with the tally of each department's pipelines"() {
        when:
        open('/products')

        then:
        assertThat(departmentNames()).hasText(DEPARTMENTS as String[])
        assertThat(department('Corporate Technology').locator('.tally')).hasText('3 DevSecOps pipelines for 1 product')
        assertThat(department('Fund Services').locator('.tally')).hasText('6 DevSecOps pipelines for 1 product · 5 active')
        assertThat(department('AI Lab').locator('.tally')).hasText('0 DevSecOps pipelines for 0 products')
        assertThat(department('AI Lab').locator('.no-products')).hasText('No products in AI Lab yet.')
        assertThat(department('Corporate Technology').locator('td a.name')).hasText('CertScanner')
        assertThat(department('Fund Services').locator('td a.name')).hasText('Payments Hub')
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        assertThat(page.locator('.toolbar .count')).hasText('2 products in 5 departments')
        assertThat(row('Payments Hub').locator('td').nth(2)).hasText('4')
        assertThat(row('Payments Hub').locator('.pipelines strong')).hasText('6')
        assertThat(row('Payments Hub').locator('.pipelines .muted')).hasText('· 5 active')
        assertThat(row('Payments Hub').locator('.pipelines .revoked')).hasText('· 1 invalidated')
        assertThat(row('CertScanner').locator('.pipelines .muted')).hasText('· 3 active')
        assertThat(row('CertScanner').locator('.pipelines .revoked')).hasCount(0)
        api.requests('GET', '/api/products')*.params() == [[:]]
        api.requests('GET', '/api/departments').size() == 1
        ownErrors().isEmpty()
    }

    def "products without a department are listed last until they are edited"() {
        given:
        def products = StubApi.fixture('products.json') as List<Map>
        api.respond('GET', '/api/products', [products[0], products[1] + [departmentId: null, departmentName: null]])

        when:
        open('/products')

        then:
        assertThat(departmentNames()).hasText((DEPARTMENTS + 'Not in a department') as String[])
        assertThat(department('Not in a department').locator('td a.name')).hasText('Payments Hub')
        assertThat(department('Not in a department').locator('.tally')).hasText('6 DevSecOps pipelines for 1 product · 5 active')
        assertThat(department('Not in a department').locator('.hint')).hasText('Edit these products to choose their department.')
        assertThat(department('Not in a department').getByRole(AriaRole.BUTTON)).hasCount(0)
        ownErrors().isEmpty()
    }

    def "searching asks the API for the trimmed term and says when nothing matches"() {
        given:
        open('/products')

        when:
        search().fill('  Payments  ')

        then:
        assertThat(names()).hasText(['Payments Hub'] as String[])
        assertThat(departmentNames()).hasText(['Fund Services'] as String[])
        assertThat(department('Fund Services').locator('.tally')).hasText('6 DevSecOps pipelines for 1 product · 5 active')
        assertThat(page.locator('.toolbar .count')).hasText('1 product in 1 department')
        api.lastRequest('GET', '/api/products').params() == [search: 'Payments']

        when:
        search().fill('corporate')

        then:
        assertThat(names()).hasText(['CertScanner'] as String[])
        assertThat(departmentNames()).hasText(['Corporate Technology'] as String[])

        when:
        search().fill('ledger-x')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No product matches "ledger-x"')
        assertThat(page.locator('.empty-state')).containsText('Try another name, code, team or department.')
        assertThat(page.locator('.toolbar .count')).hasText('0 products in 0 departments')

        when:
        search().fill('')

        then:
        assertThat(names()).hasText(['CertScanner', 'Payments Hub'] as String[])
        assertThat(departmentNames()).hasText(DEPARTMENTS as String[])
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
        assertThat(page.locator('.breadcrumb > :not(.sep)'))
                .hasText(['DevSecOps Product Management', 'Fund Services', 'Payments Hub'] as String[])
        assertThat(holding(page.locator('.page-header .meta div'), "dt:text-is('Department')").locator('dd')).hasText('Fund Services')
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
        holding(page.locator('tr.mat-mdc-row'), "a.name:text-is('${name}')")
    }

    Locator search() {
        page.getByLabel('Search products')
    }
}
