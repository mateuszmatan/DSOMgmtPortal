package com.bbh.itss.dso.portal.gui.smoke

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole
import spock.lang.IgnoreIf

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class GuiSmokeSpec extends GuiSpecification {

    static final List<Map<String, String>> SECTIONS = [
            [label: 'Product Management', heading: 'DevSecOps Product Management', path: '/products',
             description: 'Products, services, pipelines and keys'],
            [label: 'Pipeline Monitoring', heading: 'DevSecOps Pipeline Monitoring', path: '/monitoring',
             description: 'Pipeline status and DORA metrics'],
            [label: 'Change Evidence', heading: 'DevSecOps Change Evidence', path: '/evidence',
             description: 'Builds, tests and scans for ServiceNow changes'],
            [label: 'Global Settings', heading: 'DevSecOps Global Settings', path: '/settings',
             description: 'Tools, policy and defaults of every pipeline']]

    def "the portal shows its title, the short main menu and the footer"() {
        when:
        open('/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH DevSecOps Management Portal')
        assertThat(page.locator('nav.menu a')).hasText(SECTIONS*.label as String[])
        SECTIONS.every { section -> !page.locator('nav.menu').textContent().contains(section.description) }
        assertThat(page.locator('footer')).containsText('BBH 2026')
        ownErrors().isEmpty()
    }

    def "every menu link opens its section with the full name and the description on top"() {
        given:
        open('/monitoring')

        expect:
        (SECTIONS.drop(2) + SECTIONS.take(2)).every { section ->
            menuLink(section.label).click()
            page.waitForURL("**${section.path}")
            assertThat(page.locator('h1')).hasText(section.heading)
            assertThat(page.locator('.page-header .page-description')).hasText(section.description)
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
        path            | heading
        '/products'     | 'DevSecOps Product Management'
        '/monitoring'   | 'DevSecOps Pipeline Monitoring'
        '/evidence'     | 'DevSecOps Change Evidence'
        '/settings'     | 'DevSecOps Global Settings'
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
        path                      | heading
        '/products/1'             | 'CertScanner'
        '/products/2/edit'        | 'Edit Payments Hub'
        '/monitoring/products/1'  | 'CertScanner'
        '/monitoring/pipelines/1' | 'Full pipeline'
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "#path shows no icons"() {
        when:
        open(path)

        then:
        page.locator('mat-icon').count() == 0
        ownErrors().isEmpty()

        where:
        path << ['/products', '/products/1', '/monitoring', '/monitoring/products/1', '/monitoring/pipelines/1',
                 '/evidence', '/settings']
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "the service editor keeps icons only in its vertical section menu"() {
        when:
        open('/products/1/edit')
        page.locator('mat-expansion-panel-header').first().click()

        then:
        assertThat(page.locator('.rail mat-icon').first()).isVisible()
        page.locator('mat-icon').count() == page.locator('.rail mat-icon').count()
        ownErrors().isEmpty()
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "#path fits a #width px window without horizontal scrolling"() {
        given:
        page.setViewportSize(width, 900)

        when:
        open(path)

        then:
        (page.evaluate('() => document.documentElement.scrollWidth - document.documentElement.clientWidth') as int) == 0
        ownErrors().isEmpty()

        where:
        [path, width] << [['/products', '/products/1', '/products/1/edit', '/monitoring', '/monitoring/products/1',
                           '/evidence', '/settings'], [800, 600]].combinations()
    }

    def "an unknown address falls back to the product list"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/products')
        assertThat(page.locator('h1')).hasText('DevSecOps Product Management')
    }

    def menuLink(String label) {
        page.locator('nav.menu').getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    List<String> ownErrors() {
        def origin = baseUrl()
        consoleErrors.findAll { !it.contains('localhost:3000') && !it.contains('grafana') } +
                failedRequests.findAll { it.contains(origin) }
    }
}
