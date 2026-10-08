package com.bbh.itss.dso.portal.gui.smoke

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Page
import spock.lang.IgnoreIf

import static com.bbh.itss.dso.portal.gui.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.MENUITEM

class GuiSmokeSpec extends GuiSpecification {

    static final List<Map<String, String>> SECTIONS = [
            [menu       : 'DevSecOps Management', label: 'Self-service', heading: 'DevSecOps Self-service', path: '/self-service',
             description: 'Set up or change the DevSecOps pipelines of your product, step by step'],
            [menu       : 'DevSecOps Management', label: 'Pipeline Monitoring', heading: 'DevSecOps Pipeline Monitoring',
             path       : '/monitoring', description: 'Pipeline status and DORA metrics'],
            [menu       : 'DevSecOps Management', label: 'Change Evidence', heading: 'DevSecOps Change Evidence', path: '/evidence',
             description: 'Builds, tests and scans for ServiceNow changes'],
            [menu       : 'DevSecOps Management', label: 'Admin', heading: 'DevSecOps Admin', path: '/admin/products',
             description: 'Departments, products, services and the DSOEnhanced library defaults'],
            [menu       : 'Beadle', label: 'Changes', heading: 'ProTech Changes', path: '/beadle/changes',
             description: 'The ProTech changes of your department, read from ProTech each time you open them'],
            [menu       : 'Beadle', label: 'New Change', heading: 'New ProTech Change', path: '/beadle/new-change',
             description: 'Raise a ProTech change (CHG) with its change tasks (CTASK), written from Jira'],
            [menu       : 'Beadle', label: 'Admin', heading: 'Beadle Admin', path: '/beadle/admin/products',
             description: 'Departments, products and the change template of each product']]

    static final Map<String, Map<String, String>> ADMIN_TABS = [
            'DevSecOps Admin': [Departments: '/admin/departments', Products: '/admin/products', 'Library defaults': '/admin/settings'],
            'Beadle Admin'   : [Departments: '/beadle/admin/departments', Products: '/beadle/admin/products']]

    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'

    static final String GENERATED_KEY = '9c4e1a7b-2d3f-4e5a-8b6c-7d8e9f0a1b2c'

    static final String CORPORATE_TECHNOLOGY = "localStorage.setItem('dso.beadle.department', '3')"

    def "the portal shows its title, the Beadle and DevSecOps Management menus and the footer"() {
        when:
        open('/admin/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH DevSecOps Management Portal')
        assertThat(page.locator('nav.menu button')).hasText(MENUS.keySet() as String[])
        assertThat(page.locator('nav.menu .menu-group.active')).hasText('DevSecOps Management')
        MENUS.every { name, labels ->
            menuButton(name).click()
            def panel = page.locator('.mat-mdc-menu-panel')
            assertThat(panel.getByRole(MENUITEM)).hasText(labels as String[])
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
        open('/admin/products')

        then:
        assertThat(page.locator('section.department h2').first()).isVisible()
        assertThat(page.locator('section.department .tally').first())
                .hasText(~/^\d+ DevSecOps pipelines? for \d+ products?( · \d+ active)?$/)
        assertThat(page.locator('.toolbar .count')).hasText(~/^\d+ products? in \d+ departments?$/)
        ownErrors().isEmpty()
    }

    def "every menu link opens its section with the full name and the description on top"() {
        given:
        open('/monitoring')

        expect:
        (SECTIONS.drop(2) + SECTIONS.take(2)).every { section ->
            menuLink(section.menu, section.label).click()
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
        [path, heading] << SECTIONS.collect { [it.path, it.heading] } +
                ADMIN_TABS.collectMany { heading, tabs -> tabs.values().collect { [it, heading] } }.unique() +
                [['/admin', 'DevSecOps Admin'], ['/admin/products/new', 'Add product'], ['/beadle', 'ProTech Changes'],
                 ['/beadle/changes/new', 'New ProTech Change']]
    }

    def "every tab of #heading opens its page with the tab marked"() {
        given:
        open(tabs.values().first())

        expect:
        assertThat(page.locator('nav.tab-bar a')).hasText(tabs.keySet() as String[])
        tabs.every { label, path ->
            tab(label).click()
            page.waitForURL("**$path")
            assertThat(page.locator('h1')).hasText(heading)
            assertThat(page.locator('nav.tab-bar a.active')).hasText(label)
            assertThat(tab(label)).hasAttribute('aria-current', 'page')
            assertThat(page.locator('.page.admin > router-outlet + *')).hasCount(1)
            true
        }
        ownErrors().isEmpty()

        where:
        heading << ADMIN_TABS.keySet()
        tabs = ADMIN_TABS[heading]
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "#path shows #heading from the API"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        path                       | heading
        '/admin/products/1'        | 'CertScanner'
        '/admin/products/2/edit'   | 'Edit Payments Hub'
        '/monitoring/products/1'   | 'CertScanner'
        '/monitoring/pipelines/1'  | 'Full pipeline'
        '/beadle/admin/products/1' | 'CertScanner'
        '/beadle/changes/4'        | 'CHG0031001'
        '/beadle/changes/4/edit'   | 'Edit CHG0031001'
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "#path shows no icons but the magnifiers of the lookups"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        assertThat(page.locator('button.lookup mat-icon')).hasCount(lookups)
        page.locator('mat-icon').count() == lookups
        page.locator('mat-icon').allTextContents().every { it.trim() == 'search' }
        ownErrors().isEmpty()

        where:
        path                        | lookups
        '/self-service'             | 0
        '/monitoring'               | 0
        '/monitoring/products/1'    | 0
        '/monitoring/pipelines/1'   | 0
        '/evidence'                 | 0
        '/admin/departments'        | 0
        '/admin/products'           | 0
        '/admin/products/1'         | 0
        '/admin/settings'           | 0
        '/beadle/changes'           | 0
        '/beadle/new-change'        | 0
        '/beadle/changes/4'         | 0
        '/beadle/changes/4/edit'    | 13
        '/beadle/changes/2'         | 0
        '/beadle/admin/departments' | 0
        '/beadle/admin/products'    | 0
        '/beadle/admin/products/1'  | 13
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "the service editor keeps icons only in its vertical section menu"() {
        when:
        open('/admin/products/1/edit')
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
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        (page.evaluate('() => document.documentElement.scrollWidth - document.documentElement.clientWidth') as int) == 0
        ownErrors().isEmpty()

        where:
        [path, width] << [['/self-service', '/monitoring', '/monitoring/products/1', '/evidence', '/admin/departments',
                           '/admin/products', '/admin/products/1', '/admin/products/1/edit', '/admin/settings',
                           '/beadle/changes', '/beadle/new-change', '/beadle/changes/4', '/beadle/changes/4/edit',
                           '/beadle/changes/2', '/beadle/admin/departments', '/beadle/admin/products',
                           '/beadle/admin/products/1'], [800, 600]].combinations()
    }

    @IgnoreIf({ GuiSpecification.remoteBaseUrl() })
    def "an invalidated pipeline key is regenerated with a visible text button"() {
        given:
        api.respond('POST', '/api/pipelines/9/keys', fixture('pipeline-9-regenerated.json'))

        when:
        open('/admin/products/2')
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
        def saved = fixture('product-1.json') as Map
        def services = saved.services as List<Map>
        saved.services = [services[0], services[0] + [id: 7, name: 'gui-copy'], services[1]]
        saved.version = (saved.version as int) + 1
        api.on('PUT', '/api/products/1') { saved }
        api.get('/api/products/1') { productSaved() ? saved : fixture('product-1.json') }
        api.get('/api/products/1/pipelines') {
            fixture(productSaved() ? 'product-1-pipelines-with-new-service.json' : 'product-1-pipelines.json')
        }

        when:
        open('/admin/products/1/edit')
        page.locator('mat-expansion-panel-header').first().click()
        button('Duplicate').first().click()
        button('Save changes').click()
        page.waitForURL('**/admin/products/1')

        then:
        assertThat(page.locator('.generated')).containsText('Pipeline key generated for the new service gui-copy.')
        assertThat(page.locator('.key-value', new Page.LocatorOptions().setHasText(GENERATED_KEY))).isVisible()
        api.lastRequest('PUT', '/api/products/1').json().services*.id == [1, null, 2]
        ownErrors().isEmpty()
    }

    def "an unknown address falls back to pipeline monitoring"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/monitoring')
        assertThat(page.locator('h1')).hasText('DevSecOps Pipeline Monitoring')
    }

    boolean productSaved() {
        !api.requests('PUT', '/api/products/1').isEmpty()
    }
}
