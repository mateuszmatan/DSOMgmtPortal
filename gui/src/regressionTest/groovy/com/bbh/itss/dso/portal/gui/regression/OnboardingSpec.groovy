package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.ProductStore
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.bbh.itss.dso.portal.gui.support.StubResponse
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class OnboardingSpec extends EditorSpecification {

    static final String API_APPLICATION = '4b1c2d3e-1111-4a5b-8c9d-0e1f2a3b4c5d'
    static final String GUI_APPLICATION = '4b1c2d3e-2222-4a5b-8c9d-0e1f2a3b4c5d'
    static final String TAKEN_APPLICATION = '209f44ac-dd06-4ca0-884e-d944904f8020'
    static final String APP_SCAN_KEY = 'bbh_9a1b2c3d-0000-4abc-9def-123456789abc'
    static final String SAST_KEY_OF_GUI = '2c0ca4f4-a1a6-472a-9685-0c75f22fe713'

    def "a product manager adds a new product with a Security pipeline step by step"() {
        given:
        recordClipboard()
        api.respond('POST', '/api/products', StubResponse.problem(400, 'Bad Request',
                'The portal did not accept some values.',
                [errors: [[field: 'services[1].appScan.applicationId', message: 'the AppScan application belongs to CertScanner']]]))

        when:
        open('/beadle')
        link('Start', true).click()
        page.waitForURL('**/beadle/onboarding')
        button('Continue', true).click()

        then:
        assertThat(page.locator('h1')).hasText('Product Onboarding')
        assertThat(page.locator('.step-bar .step-label'))
                .hasText(['Pipeline', 'Product', 'Services', 'Review', 'Next steps'] as String[])
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('.tile-label')).hasText(['Static scan', 'Security', 'Full'] as String[])
        assertThat(choiceError()).hasText('Choose a pipeline to continue')

        when:
        tile(step(), 'Security').click()

        then:
        assertThat(tile(step(), 'Security')).hasAttribute('aria-checked', 'true')
        assertThat(step().locator('.prepare li')).hasCount(4)

        when:
        button('Continue', true).click()
        input(step(), 'Product name').fill('CertScanner')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(tile(step(), 'A new product')).hasAttribute('aria-checked', 'true')
        hasErrors(step(), ['Product name'      : 'This product is already in the portal: choose "A product in the portal" above',
                           'AppScan API key ID': 'Required'])

        when:
        fillIn(step(), ['Product name': 'Trade Archive', 'Owner team': 'Custody Technology',
                        'AppScan API key ID': APP_SCAN_KEY])

        then:
        assertThat(hintOf(step(), 'Product name')).hasText('Its code will be TRADEARCHIVE')

        when:
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Which services does Trade Archive have?')
        assertThat(choiceError()).hasText('Add at least one service')

        when:
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': 'archive-api', 'What it does': 'REST API of the archive',
                          'AppScan application ID': API_APPLICATION])
        dialogButton('Next').click()
        dialogButton('Add service').click()

        then:
        assertThat(dialog().locator('.page-count')).hasText('Part 2 of 2 · Build and run')
        assertThat(dialog().locator('.choice-error'))
                .hasText(['Choose Gradle or Maven', 'Choose where the service runs'] as String[])

        when:
        tile(dialog(), 'Maven').click()
        tile(dialog(), 'OpenShift').click()
        input(dialog(), 'OpenShift project').fill('cus-archive')
        dialogButton('Add service').click()
        addService('archive-gui', TAKEN_APPLICATION, 'Gradle', 'Virtual machines')

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(step().locator('.service-list strong')).hasText(['archive-api', 'archive-gui'] as String[])
        assertThat(step().locator('.service-list .muted')).hasText(['Maven · runs on OpenShift · project cus-archive',
                                                                    'REST API of the archive',
                                                                    'Gradle · runs on Virtual machines'] as String[])

        when:
        button('Continue', true).click()
        button('Save and create the pipelines', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(page.locator('.save-problem strong')).hasText('The portal did not accept some values.')
        assertThat(page.locator('.save-problem .problems li'))
                .hasText(['archive-gui, AppScan application ID: the AppScan application belongs to CertScanner'] as String[])

        when:
        def store = ProductStore.created(api, 3)
        page.locator('.step-bar').getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setHasText('Services')).click()
        button('Change', true).nth(1).click()
        input(dialog(), 'AppScan application ID').fill(GUI_APPLICATION)
        dialogButton('Next').click()
        dialogButton('Save service').click()
        button('Continue', true).click()
        button('Save and create the pipelines', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Trade Archive is ready. Do these steps in order')
        def request = awaitRequest('POST', '/api/products', 2)
        request.params() == [pipelineType: 'SECURITY']
        with(request.json() as Map) {
            code == 'TRADEARCHIVE'
            name == 'Trade Archive'
            ownerTeam == 'Custody Technology'
            contactEmail == null
            appScan.keyId == OnboardingSpec.APP_SCAN_KEY
            services*.name == ['archive-api', 'archive-gui']
            services*.appScan*.applicationId == [OnboardingSpec.API_APPLICATION, OnboardingSpec.GUI_APPLICATION]
            services*.build*.tool == ['MAVEN', 'GRADLE']
            services*.deployment*.target == ['OPENSHIFT', 'VM']
            services[0].openShiftTargets.RD.projectDeployment == 'cus-archive-rd'
            services[0].openShiftTargets.QC.projectDeployment == 'cus-archive-qc'
            services[1].openShiftTargets == [:]
        }
        store.services*.pipelines*.type == [['SECURITY'], ['SECURITY']]
        assertThat(page.locator('.jenkinsfile .code')).hasText(store.services.collect { service ->
            "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys[service.serviceName]}')".toString()
        } as String[])
        assertThat(page.locator('.next-steps > li h3')).hasText(['Put the Jenkinsfile in each repository',
                                                                'Create a Jenkins job for each service',
                                                                'Run each job once', 'Follow the results'] as String[])
        assertThat(page.locator('.next-steps code').last()).hasText('DevSecOps/TRADEARCHIVE/archive-api-security')

        when:
        button('Copy', true).first().click()

        then:
        assertThat(snackBar()).containsText('Jenkinsfile copied to the clipboard')
        copiedTexts() == ["@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys['archive-api']}')\n".toString()]
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "a product in the portal gets a Static scan pipeline for every service, a new service included"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def stored = StubApi.fixture('product-1.json') as Map

        when:
        open('/beadle/onboarding')
        tile(step(), 'Static scan').click()
        button('Continue', true).click()
        tile(step(), 'A product in the portal').click()
        button('Continue', true).click()

        then:
        assertThat(choiceError()).hasText('Choose the product')

        when:
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')

        then:
        assertThat(step().locator('dl.rows dd')).hasText(['Technology Architecture', '2'] as String[])

        when:
        button('Continue', true).click()

        then:
        assertThat(step().locator('.service-list .muted').first()).hasText('Gradle · runs on Virtual machines')
        assertThat(button('Remove', true)).hasCount(0)

        when:
        button('Change', true).first().click()

        then:
        assertThat(dialog().locator('h2')).hasText('Change gui')
        assertThat(dialog().locator('.page-count')).hasCount(0)
        assertThat(dialog().locator('.note')).containsText('Built with Gradle, runs on Virtual machines.')

        when:
        input(dialog(), 'What it does').fill('Web front end')
        dialogButton('Save service').click()
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': 'scanner', 'AppScan application ID': API_APPLICATION])
        dialogButton('Next').click()

        then:
        assertThat(dialog().getByRole(AriaRole.RADIOGROUP)).hasCount(1)

        when:
        tile(dialog(), 'Maven').click()
        dialogButton('Add service').click()

        then:
        assertThat(step().locator('.service-list strong')).hasText(['gui', 'backend-api', 'scanner'] as String[])
        assertThat(button('Remove', true)).hasCount(1)

        when:
        button('Continue', true).click()

        then:
        assertThat(step().locator('.lead'))
                .hasText('Saving updates CertScanner and gives every service a Static scan pipeline with its own key, unless it has one already.')

        when:
        button('Save and create the pipelines', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is ready. Do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'SAST']
        with(request.json() as Map) {
            version == stored.version
            services*.id == [1, 2, null]
            services*.description == ['Web front end', stored.services[1].description, null]
            services[1].build == stored.services[1].build
            services[1].deployment == stored.services[1].deployment
            services[2].build.tool == 'MAVEN'
            services[2].deployment.target == 'VM'
        }
        store.services*.pipelines*.type == [['FULL', 'SAST'], ['FULL', 'SAST'], ['SAST']]
        store.generatedKeys.keySet() == ['backend-api', 'scanner'] as Set
        assertThat(page.locator('.jenkinsfile .code')).containsText([SAST_KEY_OF_GUI, store.generatedKeys['backend-api'],
                                                                    store.generatedKeys['scanner']] as String[])
        assertThat(page.locator('.next-steps > li')).hasCount(4)
        assertThat(page.locator('.next-steps code').last()).hasText('DevSecOps/CERTSCANNER/gui-sast')

        when:
        button('Onboard another product', true).click()

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('[role=radio][aria-checked=true]')).hasCount(0)
        ownErrors().isEmpty()
    }

    def "leaving the wizard with answers asks before they are lost"() {
        when:
        open('/beadle/onboarding')
        tile(step(), 'Full').click()
        button('Continue', true).click()
        input(step(), 'Product name').fill('Trade Archive')
        menuLink('Overview').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(currentStep()).hasText('Product')
        assertThat(input(step(), 'Product name')).hasValue('Trade Archive')

        when:
        menuLink('Overview').click()
        dialogButton('Discard').click()
        page.waitForURL('**/beadle')

        then:
        assertThat(page.locator('h1')).hasText('Beadle')
        ownErrors().isEmpty()
    }

    void addService(String name, String applicationId, String tool, String target) {
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': name, 'AppScan application ID': applicationId])
        dialogButton('Next').click()
        tile(dialog(), tool).click()
        tile(dialog(), target).click()
        dialogButton('Add service').click()
    }

    Locator step() {
        page.locator('section.step')
    }

    Locator currentStep() {
        page.locator('.step-bar .current .step-label')
    }

    Locator choiceError() {
        step().locator('.choice-error')
    }

    Locator tile(Locator scope, String label) {
        scope.getByRole(AriaRole.RADIO, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }
}
