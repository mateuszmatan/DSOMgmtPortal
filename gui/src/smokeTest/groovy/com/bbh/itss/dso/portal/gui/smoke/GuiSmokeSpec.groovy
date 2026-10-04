package com.bbh.itss.dso.portal.gui.smoke

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.Page
import spock.lang.IgnoreIf

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class GuiSmokeSpec extends GuiSpecification {

    static final List<String> TABS = ['DevSecOps Product Management', 'DevSecOps Pipeline Monitoring',
                                      'DevSecOps Change Evidence', 'DevSecOps Global Settings']

    def "the portal shows its title, the four tabs and the footer"() {
        when:
        open('/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH DevSecOps Management Portal')
        TABS.every { tab -> page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(tab)).count() == 1 }
        assertThat(page.locator('footer')).containsText('BBH 2026')
        ownErrors().isEmpty()
    }

    def "every tab opens its section"() {
        given:
        open('/products')

        expect:
        [['DevSecOps Pipeline Monitoring', '/monitoring'], ['DevSecOps Change Evidence', '/evidence'],
         ['DevSecOps Global Settings', '/settings'], ['DevSecOps Product Management', '/products']].every { tab, path ->
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(tab)).click()
            page.waitForURL("**$path")
            assertThat(page.locator('h1')).hasText(tab)
            true
        }
        ownErrors().isEmpty()
    }

    def "#path opens from a direct link"() {
        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        path         | heading
        '/products'  | 'DevSecOps Product Management'
        '/monitoring' | 'DevSecOps Pipeline Monitoring'
        '/evidence'  | 'DevSecOps Change Evidence'
        '/settings'  | 'DevSecOps Global Settings'
        '/products/new' | 'Add product'
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "#path shows #heading from the API"() {
        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        path                     | heading
        '/products/1'            | 'CertScanner'
        '/products/2/edit'       | 'Edit Payments Hub'
        '/monitoring/products/1' | 'CertScanner'
        '/monitoring/pipelines/1' | 'Full pipeline'
    }

    def "an unknown address falls back to the product list"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/products')
        assertThat(page.locator('h1')).hasText('DevSecOps Product Management')
    }

    List<String> ownErrors() {
        def origin = baseUrl()
        consoleErrors.findAll { !it.contains('localhost:3000') && !it.contains('grafana') } +
                failedRequests.findAll { it.contains(origin) }
    }
}
