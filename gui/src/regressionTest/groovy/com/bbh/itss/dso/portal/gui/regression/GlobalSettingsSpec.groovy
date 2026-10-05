package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.bbh.itss.dso.portal.gui.support.RecordedRequest
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.bbh.itss.dso.portal.gui.support.StubResponse
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class GlobalSettingsSpec extends GuiSpecification {

    static final String SAVED_AT = '2026-10-05T11:00:00Z'

    Map stored

    def setup() {
        stored = StubApi.fixture('settings.json') as Map
        api.get('/api/settings') { StubResponse.json(stored) }
        api.on('PUT', '/api/settings') { RecordedRequest request ->
            def body = request.json() as Map
            if (body.version != stored.version) {
                return StubResponse.problem(409, 'Conflict', 'The settings were changed by someone else')
            }
            stored = body + [version: (stored.version as int) + 1, updatedAt: SAVED_AT]
            StubResponse.json(stored)
        }
    }

    def "saving the settings unchanged sends back what was loaded with its version"() {
        given:
        def loaded = StubApi.fixture('settings.json') as Map
        open('/settings')

        expect:
        assertThat(page.locator('.page-header .meta')).containsText('Version 1')

        when:
        button('Save settings', true).click()

        then:
        assertThat(snackBar()).containsText('DevSecOps Global Settings saved')
        api.awaitRequest('PUT', '/api/settings').json() == loaded.findAll { it.key != 'updatedAt' }
        assertThat(page.locator('.page-header .meta')).containsText('Version 2')
        ownErrors().isEmpty()
    }

    def "an edited setting is saved with the loaded version and the page shows the saved values"() {
        given:
        open('/settings')

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName('GoldenFix runs by default')).uncheck()

        then:
        assertThat(page.locator('.save-bar')).containsText('Unsaved changes')

        when:
        button('Save settings', true).click()

        then:
        def body = api.awaitRequest('PUT', '/api/settings').json() as Map
        body.version == 1
        body.platform.jenkinsUrl == 'https://jenkins2.bbh.com/'
        body.goldenFix.enabled == false
        body.findAll { !(it.key in ['platform', 'goldenFix']) } ==
                (StubApi.fixture('settings.json') as Map).findAll { !(it.key in ['platform', 'goldenFix', 'updatedAt']) }
        assertThat(page.locator('.page-header .meta')).containsText('Version 2')
        assertThat(page.locator('.save-bar')).not().containsText('Unsaved changes')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins2.bbh.com/')
        ownErrors().isEmpty()
    }

    def "a save over someone else's newer settings is refused with a conflict message and recovered by reloading"() {
        given:
        open('/settings')
        stored = stored + [version: 2, updatedAt: SAVED_AT, platform: (stored.platform as Map) + [oisHost: 'ois2.bbh.com']]

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Save settings', true).click()

        then:
        assertThat(page.locator('.banner.conflict')).containsText('Someone else saved the settings after you opened this page.')
        assertThat(page.locator('.banner.conflict')).containsText('Your changes were not saved, so nothing was overwritten.')
        assertThat(page.locator('.save-bar .save-error')).hasText('Not saved: the settings were changed by someone else.')
        assertThat(button('Save settings', true)).isDisabled()
        api.awaitRequest('PUT', '/api/settings').json().version == 1

        when:
        page.locator('.banner.conflict').getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName('Reload')).click()

        then:
        assertThat(page.locator('.banner.conflict')).hasCount(0)
        assertThat(field('OIS host')).hasValue('ois2.bbh.com')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
        assertThat(page.locator('.page-header .meta')).containsText('Version 2')
        assertThat(button('Save settings', true)).isEnabled()

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Save settings', true).click()

        then:
        assertThat(snackBar()).containsText('DevSecOps Global Settings saved')
        api.awaitRequest('PUT', '/api/settings', 2).json().version == 2
        stored.version == 3
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "the server's field errors are shown on their fields and the section is marked"() {
        given:
        api.respond('PUT', '/api/settings', StubResponse.problem(400, 'Bad Request', 'Some values are not valid', [errors: [
                [field: 'platform.sonarServerUrl', message: 'SonarQube does not answer at this address'],
                [field: 'audit.retentionDays', message: 'must be at least 30']]]))
        open('/settings')

        when:
        button('Save settings', true).click()

        then:
        assertThat(page.locator('.save-bar .save-error')).hasText('The portal did not accept some values. They are marked below.')
        assertThat(page.locator('mat-form-field').filter(new Locator.FilterOptions()
                .setHas(page.locator("mat-label:text-is('SonarQube server URL')"))).locator('mat-error'))
                .hasText('SonarQube does not answer at this address')
        assertThat(page.locator('.problems li')).hasText(['audit.retentionDays: must be at least 30'] as String[])
        assertThat(page.locator('.toc-item.problem')).hasText(['Platform and tools'] as String[])

        when:
        field('SonarQube server URL').fill('https://sonar.bbh.com')

        then:
        assertThat(page.locator('.toc-item.problem')).hasCount(0)
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "changes are discarded with the button or when leaving the page after confirming"() {
        given:
        open('/settings')

        when:
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Discard changes', true).click()

        then:
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
        assertThat(button('Discard changes', true)).isDisabled()

        when:
        field('Proxy host').fill('proxy2.bbh.com')
        menuLink('Product Management').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName('Discard')).click()
        page.waitForURL('**/products')

        then:
        api.requests('PUT', '/api/settings').isEmpty()
        ownErrors().isEmpty()
    }

    def "the generated configuration is the saved global section, and a failure names its problem"() {
        given:
        open('/settings')

        when:
        button('Generated configuration', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Generated global configuration')
        dialog().locator('pre.code').textContent() == StubApi.fixtureText('settings-config.yaml')
        api.awaitRequest('GET', '/api/settings/config').params() == [format: 'yaml']

        when:
        dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName('Close')).click()
        field('Jenkins URL').fill('https://jenkins2.bbh.com/')
        button('Generated configuration', true).click()

        then:
        assertThat(dialog().locator('.subtitle')).containsText('Your unsaved changes are not included.')

        when:
        dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName('Close')).click()
        api.respond('GET', '/api/settings/config', StubResponse.problem(503, 'Service Unavailable', 'The configuration renderer is restarting'))
        button('Generated configuration', true).click()

        then:
        assertThat(snackBar()).containsText('The configuration renderer is restarting')
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }
}
