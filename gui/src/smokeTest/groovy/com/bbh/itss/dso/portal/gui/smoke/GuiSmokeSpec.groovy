package com.bbh.itss.dso.portal.gui.smoke

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import spock.lang.IgnoreIf

import java.util.regex.Pattern

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
             description: 'Tools, policy and defaults of every pipeline'],
            [label: 'Overview', heading: 'Beadle', path: '/beadle', description: 'New features of the BBH portal'],
            [label: 'Product Onboarding', heading: 'Product Onboarding', path: '/beadle/onboarding',
             description: 'Set up DevSecOps for your product, step by step'],
            [label: 'Production Change', heading: 'Production Change', path: '/beadle/changes',
             description: 'Raise a ServiceNow change with its change tasks, written from Jira']]

    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'

    static final String GENERATED_KEY = '9c4e1a7b-2d3f-4e5a-8b6c-7d8e9f0a1b2c'

    def "the portal shows its title, the Beadle and DevSecOps Management menus and the footer"() {
        when:
        open('/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH DevSecOps Management Portal')
        assertThat(page.locator('nav.menu button')).hasText(MENUS.keySet() as String[])
        assertThat(page.locator('nav.menu .menu-group.active')).hasText('DevSecOps Management')
        MENUS.every { name, labels ->
            menuButton(name).click()
            def panel = page.locator('.mat-mdc-menu-panel')
            assertThat(panel.getByRole(AriaRole.MENUITEM)).hasText(labels as String[])
            assert SECTIONS.every { section -> !panel.textContent().contains(section.description) }
            page.keyboard().press('Escape')
            assertThat(panel).hasCount(0)
            true
        }
        assertThat(page.locator('footer')).containsText('BBH 2026')
        ownErrors().isEmpty()
    }

    def "the product list groups the products by department with the tally of their pipelines"() {
        when:
        open('/products')

        then:
        assertThat(page.locator('section.department h2').first()).isVisible()
        assertThat(page.locator('section.department .tally').first())
                .hasText(Pattern.compile('^\\d+ DevSecOps pipelines? for \\d+ products?( · \\d+ active)?$'))
        assertThat(page.locator('.toolbar .count')).hasText(Pattern.compile('^\\d+ products? in \\d+ departments?$'))
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
        path                  | heading
        '/products'           | 'DevSecOps Product Management'
        '/monitoring'         | 'DevSecOps Pipeline Monitoring'
        '/evidence'           | 'DevSecOps Change Evidence'
        '/settings'           | 'DevSecOps Global Settings'
        '/products/new'       | 'Add product'
        '/beadle'             | 'Beadle'
        '/beadle/onboarding'  | 'Product Onboarding'
        '/beadle/changes'     | 'Production Change'
        '/beadle/changes/new' | 'Raise a production change'
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
        '/products/1/change'      | 'ServiceNow change template of CertScanner'
        '/beadle/changes/1'       | 'CHG0031001'
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
                 '/evidence', '/settings', '/beadle', '/beadle/onboarding', '/beadle/changes', '/beadle/changes/new',
                 '/beadle/changes/1', '/products/1/change']
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
                           '/evidence', '/settings', '/beadle/onboarding', '/beadle/changes', '/beadle/changes/new',
                           '/beadle/changes/1', '/products/1/change'], [800, 600]].combinations()
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "an invalidated pipeline key is regenerated with a visible text button"() {
        given:
        api.respond('POST', '/api/pipelines/9/keys', StubApi.fixture('pipeline-9-regenerated.json'))

        when:
        open('/products/2')
        def regenerate = button('Regenerate key of the SAST scanning pipeline')

        then:
        assertThat(regenerate).hasCount(1)
        assertThat(regenerate).isVisible()
        regenerate.locator('mat-icon').count() == 0

        when:
        regenerate.click()

        then:
        assertThat(page.locator('.key-value', new Page.LocatorOptions().setHasText(REGENERATED_KEY))).isVisible()
        assertThat(page.locator('mat-snack-bar-container'))
                .containsText('SAST scanning pipeline of mobile-app has a new key')
        assertThat(button('Regenerate key')).hasCount(0)
        api.requests('POST', '/api/pipelines/9/keys').size() == 1
        ownErrors().isEmpty()
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "saving a new service shows the pipeline key generated for it on the product page"() {
        given:
        def saved = StubApi.fixture('product-1.json') as Map
        def services = saved.services as List<Map>
        saved.services = [services[0], services[0] + [id: 7, name: 'gui-copy'], services[1]]
        saved.version = (saved.version as int) + 1
        api.on('PUT', '/api/products/1') { saved }
        api.get('/api/products/1') { productSaved() ? saved : StubApi.fixture('product-1.json') }
        api.get('/api/products/1/pipelines') {
            StubApi.fixture(productSaved() ? 'product-1-pipelines-with-new-service.json' : 'product-1-pipelines.json')
        }

        when:
        open('/products/1/edit')
        page.locator('mat-expansion-panel-header').first().click()
        button('Duplicate').first().click()
        button('Save changes').click()
        page.waitForURL('**/products/1')

        then:
        assertThat(page.locator('.generated')).containsText('Pipeline key generated for the new service gui-copy.')
        assertThat(page.locator('.key-value', new Page.LocatorOptions().setHasText(GENERATED_KEY))).isVisible()
        api.lastRequest('PUT', '/api/products/1').json().services*.id == [1, null, 2]
        ownErrors().isEmpty()
    }

    def "an unknown address falls back to the product list"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/products')
        assertThat(page.locator('h1')).hasText('DevSecOps Product Management')
    }

    boolean productSaved() {
        !api.requests('PUT', '/api/products/1').isEmpty()
    }
}
