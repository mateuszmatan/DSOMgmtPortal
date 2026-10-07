package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.gui.support.ProductStore.created
import static com.bbh.itss.dso.portal.gui.support.ProductStore.recorded
import static com.bbh.itss.dso.portal.gui.support.StubApi.fixture
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static com.microsoft.playwright.options.AriaRole.OPTION
import static com.microsoft.playwright.options.AriaRole.RADIOGROUP

class SelfServiceSpec extends EditorSpecification {

    static final String API_APPLICATION = '4b1c2d3e-1111-4a5b-8c9d-0e1f2a3b4c5d'
    static final String GUI_APPLICATION = '4b1c2d3e-2222-4a5b-8c9d-0e1f2a3b4c5d'
    static final String TAKEN_APPLICATION = '209f44ac-dd06-4ca0-884e-d944904f8020'
    static final String APP_SCAN_KEY = 'bbh_9a1b2c3d-0000-4abc-9def-123456789abc'
    static final String SAST_KEY_OF_GUI = '2c0ca4f4-a1a6-472a-9685-0c75f22fe713'
    static final String SECURITY_KEY_OF_GATEWAY = '5a07b656-ed5b-46b6-b3af-eb043ca15627'

    def "a product manager adds a new product with a Security pipeline step by step"() {
        given:
        recordClipboard()
        api.respond('POST', '/api/products', problem(400, 'Bad Request', 'The portal did not accept some values.',
                [errors: [[field: 'services[1].appScan.applicationId', message: 'the AppScan application belongs to CertScanner']]]))

        when:
        open('/monitoring')
        menuLink('Self-service').click()
        page.waitForURL('**/self-service')
        input(step(), 'Product name').fill('CertScanner')
        button('Continue', true).click()

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Self-service')
        assertThat(page.locator('.step-bar .step-label'))
                .hasText(['Product', 'Pipeline', 'Services', 'Review', 'Next steps'] as String[])
        assertThat(currentStep()).hasText('Product')
        assertThat(radio(step(), 'A new product')).hasAttribute('aria-checked', 'true')
        assertThat(step().locator('.fields mat-label').first()).hasText('Department')
        hasErrors(step(), ['Department'        : 'Required',
                           'Product name'      : 'This product is already in the portal: choose "A product in the portal" above',
                           'AppScan API key ID': 'Required'])

        when:
        choose(step(), 'Department', 'Custody')
        fillIn(step(), ['Product name': 'Trade Archive', 'Owner team': 'Custody Technology',
                        'AppScan API key ID': APP_SCAN_KEY])

        then:
        assertThat(hintOf(step(), 'Product name')).hasText('Its code will be TRADEARCHIVE')

        when:
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('h2')).hasText('Which pipeline does Trade Archive need?')
        assertThat(step().locator('.tile-label')).hasText(['Static scan', 'Security', 'Full'] as String[])
        assertThat(step().locator('.tile-note')).hasCount(0)
        assertThat(step().locator('.today')).hasCount(0)
        assertThat(choiceError()).hasText('Choose a pipeline to continue')

        when:
        radio(step(), 'Security').click()

        then:
        assertThat(radio(step(), 'Security')).hasAttribute('aria-checked', 'true')
        assertThat(step().locator('.prepare li')).hasCount(4)

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
        radio(dialog(), 'Maven').click()
        radio(dialog(), 'OpenShift').click()
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
        button('Create the pipelines', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(review('Department')).hasText('Custody')
        assertThat(review('Added').locator('li')).hasText(['archive-api · Maven · runs on OpenShift · project cus-archive',
                                                           'archive-gui · Gradle · runs on Virtual machines'] as String[])
        assertThat(page.locator('.save-problem strong')).hasText('The portal did not accept some values.')
        assertThat(page.locator('.save-problem .problems li'))
                .hasText(['archive-gui, AppScan application ID: the AppScan application belongs to CertScanner'] as String[])

        when:
        def store = created(api, 3)
        page.locator('.step-bar').getByRole(BUTTON).filter(new Locator.FilterOptions().setHasText('Services')).click()
        button('Change', true).nth(1).click()
        input(dialog(), 'AppScan application ID').fill(GUI_APPLICATION)
        dialogButton('Next').click()
        dialogButton('Save service').click()
        button('Continue', true).click()
        button('Create the pipelines', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Trade Archive is ready. Do these steps in order')
        def request = awaitRequest('POST', '/api/products', 2)
        request.params() == [pipelineType: 'SECURITY']
        with(request.json() as Map) {
            code == 'TRADEARCHIVE'
            name == 'Trade Archive'
            departmentId == 4
            ownerTeam == 'Custody Technology'
            contactEmail == null
            appScan.keyId == SelfServiceSpec.APP_SCAN_KEY
            services*.name == ['archive-api', 'archive-gui']
            services*.appScan*.applicationId == [SelfServiceSpec.API_APPLICATION, SelfServiceSpec.GUI_APPLICATION]
            services*.build*.tool == ['MAVEN', 'GRADLE']
            services*.deployment*.target == ['OPENSHIFT', 'VM']
            services[0].openShiftTargets.RD.projectDeployment == 'cus-archive-rd'
            services[0].openShiftTargets.QC.projectDeployment == 'cus-archive-qc'
            services[1].openShiftTargets == [:]
        }
        store.services*.pipelines*.type == [['SECURITY'], ['SECURITY']]
        assertThat(page.locator('.jenkinsfile .code-block')).hasText(store.services.collect { service ->
            "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys[service.serviceName]}')".toString()
        } as String[])
        assertThat(page.locator('.next-steps > li h3')).hasText(['Put the Jenkinsfile in each repository',
                                                                'Create a Jenkins job for each service',
                                                                'Run each job once', 'Follow the results'] as String[])
        assertThat(page.locator('.next-steps code').last()).hasText('DevSecOps/TRADEARCHIVE/archive-api-security')
        assertThat(link('Open in DevSecOps Admin', true)).hasAttribute('href', '/admin/products/3')

        when:
        button('Copy', true).first().click()

        then:
        assertThat(snackBar()).containsText('Jenkinsfile copied to the clipboard')
        copiedTexts() == ["@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsSecurityPipeline(pipelineKey: '${store.generatedKeys['archive-api']}')\n".toString()]
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "a product in the portal gets a Static scan pipeline next to the pipelines it has, a new service included"() {
        given:
        def store = recorded(api, 1)
        def stored = fixture('product-1.json') as Map

        when:
        open('/self-service')
        radio(step(), 'A product in the portal').click()
        button('Continue', true).click()

        then:
        assertThat(errorOf(step(), 'Department')).hasText('Required')
        assertThat(choiceError()).hasText('Choose the product')

        when:
        choose(step(), 'Department', 'Corporate Technology')
        select(step(), 'Product').click()

        then:
        assertThat(page.locator('mat-optgroup .mat-mdc-optgroup-label')).hasText(['Corporate Technology'] as String[])

        when:
        page.getByRole(OPTION, new Page.GetByRoleOptions().setName('CertScanner (CERTSCANNER)').setExact(true)).click()

        then:
        assertThat(hintOf(step(), 'Product')).hasText('1 product to choose from')
        assertThat(step().locator('dl.rows dd')).hasText(['Technology Architecture', '2'] as String[])

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('.lead'))
                .containsText('Choosing one adds it to every service that lacks it and keeps the other pipelines.')
        assertThat(step().locator('.today li')).hasText(['gui · Full, Static scan', 'backend-api · Full'] as String[])
        assertThat(step().locator('.tile-note'))
                .hasText(['1 of 2 services has it', 'No service has it yet', 'Every service has it'] as String[])

        when:
        radio(step(), 'Static scan').click()
        button('Continue', true).click()

        then:
        assertThat(step().locator('.service-list .muted').first()).hasText('Gradle · runs on Virtual machines')
        assertThat(button('Remove', true)).hasCount(2)

        when:
        buttonIn(serviceRow('gui'), 'Change').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Change gui')
        assertThat(dialog().locator('.page-count')).hasText('Part 1 of 2 · About the service')

        when:
        input(dialog(), 'What it does').fill('Web front end')
        dialogButton('Next').click()

        then:
        assertThat(dialog().getByRole(RADIOGROUP)).hasCount(1)
        assertThat(dialog().locator('.note')).hasText('If you change how it is built, its build settings go back to the BBH defaults.')

        when:
        dialogButton('Save service').click()
        addService('scanner', API_APPLICATION, 'Maven')

        then:
        assertThat(step().locator('.service-list strong')).hasText(['gui', 'backend-api', 'scanner'] as String[])
        assertThat(step().locator('.service-list .tag')).hasText(['Changed', 'New'] as String[])
        assertThat(button('Remove', true)).hasCount(3)

        when:
        button('Continue', true).click()

        then:
        assertThat(step().locator('.lead'))
                .hasText('Saving updates CertScanner and gives every service a Static scan pipeline with its own key, unless it has one already.')
        assertThat(review('Added').locator('li')).hasText(['scanner · Maven'] as String[])
        assertThat(review('Changed').locator('li')).hasText(['gui · new description'] as String[])
        assertThat(review('Unchanged').locator('li')).hasText(['backend-api · Maven · runs on OpenShift'] as String[])
        assertThat(step().locator('.removal-warning')).hasCount(0)

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is ready. Do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'SAST']
        with(request.json() as Map) {
            version == stored.version
            departmentId == stored.departmentId
            services*.id == [1, 2, null]
            services*.description == ['Web front end', stored.services[1].description, null]
            services[0].build == stored.services[0].build
            services[1].build == stored.services[1].build
            services[1].deployment == stored.services[1].deployment
            services[2].build.tool == 'MAVEN'
            services[2].deployment.target == 'VM'
        }
        store.services*.pipelines*.type == [['FULL', 'SAST'], ['FULL', 'SAST'], ['SAST']]
        store.generatedKeys.keySet() == ['backend-api', 'scanner'] as Set
        assertThat(page.locator('.jenkinsfile .code-block')).containsText([SAST_KEY_OF_GUI, store.generatedKeys['backend-api'],
                                                                    store.generatedKeys['scanner']] as String[])
        assertThat(page.locator('.next-steps > li')).hasCount(4)
        assertThat(page.locator('.next-steps code').last()).hasText('DevSecOps/CERTSCANNER/gui-sast')

        when:
        button('Start again', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(radio(step(), 'A new product')).hasAttribute('aria-checked', 'true')
        assertThat(input(step(), 'Product name')).hasValue('')
        ownErrors().isEmpty()
    }

    def "a product in the portal without a department is put into one on the way"() {
        given:
        def store = recorded(api, 2)
        store.product.departmentId = null
        def products = fixture('products.json') as List<Map>
        api.respond('GET', '/api/products', [products[0], products[1] + [departmentId: null, departmentName: null]])

        when:
        open('/self-service')
        radio(step(), 'A product in the portal').click()
        select(step(), 'Product').click()

        then:
        assertThat(page.locator('mat-optgroup .mat-mdc-optgroup-label')).hasText(['Not in a department'] as String[])

        when:
        page.getByRole(OPTION, new Page.GetByRoleOptions().setName('Payments Hub (PAYHUB)').setExact(true)).click()
        assertThat(step().locator('dl.rows dt')).hasText(['Owner team', 'Services'] as String[])
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Product')
        assertThat(errorOf(step(), 'Department')).hasText('Required')

        when:
        choose(step(), 'Department', 'Fund Services')

        then:
        assertThat(hintOf(step(), 'Department')).hasText('Saving moves the product into this department')
        assertThat(select(step(), 'Product')).containsText('Payments Hub (PAYHUB)')

        when:
        button('Continue', true).click()
        radio(step(), 'Static scan').click()
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(review('Department')).hasText('Fund Services')

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Payments Hub is ready. Do these steps in order')
        awaitRequest('PUT', '/api/products/2').json().departmentId == 5
        store.product.departmentId == 5
        ownErrors().isEmpty()
    }

    def "changing the build tool of a service in the portal puts its build settings back to the BBH defaults"() {
        given:
        def store = recorded(api, 1)
        def stored = fixture('product-1.json') as Map

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), 'Full').click()
        button('Continue', true).click()
        buttonIn(serviceRow('gui'), 'Change').click()
        dialogButton('Next').click()

        then:
        assertThat(radio(dialog(), 'Gradle')).hasAttribute('aria-checked', 'true')
        assertThat(radio(dialog(), 'Virtual machines')).hasAttribute('aria-checked', 'true')
        assertThat(dialog().locator('.note'))
                .hasText('If you change how it is built or where it runs, its build or deployment settings go back to the BBH defaults.')

        when:
        radio(dialog(), 'Maven').click()
        dialogButton('Save service').click()

        then:
        assertThat(serviceRow('gui').locator('.tag')).hasText('Changed')
        assertThat(serviceRow('gui').locator('.muted').first()).hasText('Maven · runs on Virtual machines')

        when:
        button('Continue', true).click()

        then:
        assertThat(review('Changed').locator('li'))
                .hasText(['gui · built with Maven instead of Gradle, with the default build settings'] as String[])
        assertThat(review('Unchanged').locator('li')).hasText(['backend-api · Maven · runs on OpenShift'] as String[])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is ready. Do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'FULL']
        with(request.json() as Map) {
            services*.id == [1, 2]
            services[0].build.tool == 'MAVEN'
            services[0].build.autoSetup
            services[0].build.javaPath == null
            services[0].build.buildPath == 'target/*.jar'
            services[0].build.command.tasks == ['clean', 'verify']
            services[0].delivery.tasks == ['deploy:deploy-file']
            services[0].deployment == stored.services[0].deployment
            services[0].testJobs*.job == stored.services[0].testJobs*.job
            services[0].appScan.applicationId == stored.services[0].appScan.applicationId
            services[1].build == stored.services[1].build
        }
        store.services*.pipelines*.type == [['FULL', 'SAST'], ['FULL']]
        store.generatedKeys.isEmpty()
        ownErrors().isEmpty()
    }

    def "removing a service of the portal deletes it with its pipelines and keys on save"() {
        given:
        def store = recorded(api, 1)

        when:
        openProduct('Corporate Technology', 'CertScanner (CERTSCANNER)')
        radio(step(), 'Security').click()
        button('Continue', true).click()
        buttonIn(serviceRow('backend-api'), 'Remove').click()

        then:
        assertThat(serviceRow('backend-api').locator('.tag')).hasText('Removed')
        assertThat(serviceRow('backend-api').locator('.removal')).hasText(
                'Saving deletes backend-api, its pipelines (Full) and their keys. Jenkins jobs that use these keys stop working.')
        assertThat(buttonIn(serviceRow('backend-api'), 'Change')).hasCount(0)

        when:
        buttonIn(serviceRow('gui'), 'Remove').click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Services')
        assertThat(choiceError()).hasText('Keep at least one service')

        when:
        buttonIn(serviceRow('gui'), 'Undo').click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(review('Removed').locator('li')).hasText(['backend-api · Maven · runs on OpenShift'] as String[])
        assertThat(review('Unchanged').locator('li')).hasText(['gui · Gradle · runs on Virtual machines'] as String[])
        assertThat(step().locator('.removal-warning strong'))
                .hasText('Saving deletes these pipelines and their keys. Jenkins jobs that use these keys stop working.')
        assertThat(step().locator('.removal-warning li')).hasText(['backend-api · Full'] as String[])

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CertScanner is ready. Do these steps in order')
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [pipelineType: 'SECURITY']
        (request.json() as Map).services*.name == ['gui']
        store.services*.serviceName == ['gui']
        store.services*.pipelines*.type == [['FULL', 'SAST', 'SECURITY']]
        assertThat(page.locator('.jenkinsfile strong')).hasText(['gui'] as String[])
        ownErrors().isEmpty()
    }

    def "adding a Security pipeline shows the pipelines the services of a product already have"() {
        given:
        def store = recorded(api, 2)

        when:
        openProduct('Fund Services', 'Payments Hub (PAYHUB)')

        then:
        assertThat(currentStep()).hasText('Pipeline')
        assertThat(step().locator('h2')).hasText('Which pipeline does Payments Hub need?')
        assertThat(step().locator('.today li')).hasText(['gateway · Full, Security, Extended', 'ledger · Full',
                                                         'notifications · Full', 'mobile-app · Static scan'] as String[])
        assertThat(step().locator('.tile-note'))
                .hasText(['1 of 4 services has it', '1 of 4 services has it', '3 of 4 services have it'] as String[])

        when:
        radio(step(), 'Security').click()
        button('Continue', true).click()

        then:
        assertThat(step().locator('.service-list strong'))
                .hasText(['gateway', 'ledger', 'notifications', 'mobile-app'] as String[])
        assertThat(serviceRow('mobile-app').locator('.muted').first()).hasText('Flutter · runs on Virtual machines')

        when:
        button('Continue', true).click()

        then:
        assertThat(step().locator('dl.rows dt')).hasText(['Product', 'Department', 'Pipeline', 'Unchanged'] as String[])
        assertThat(review('Pipeline')).hasText('Security · added to ledger, notifications and mobile-app')
        assertThat(review('Unchanged').locator('li')).hasCount(4)

        when:
        button('Save the changes', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Payments Hub is ready. Do these steps in order')
        def request = awaitRequest('PUT', '/api/products/2')
        request.params() == [pipelineType: 'SECURITY']
        (request.json() as Map).services*.id == [3, 4, 5, 6]
        store.services*.pipelines*.type == [['EXTENDED', 'FULL', 'SECURITY'], ['FULL', 'SECURITY'],
                                            ['FULL', 'SECURITY'], ['SAST', 'SECURITY']]
        store.generatedKeys.keySet() == ['ledger', 'notifications', 'mobile-app'] as Set
        assertThat(page.locator('.jenkinsfile .code-block')).containsText([SECURITY_KEY_OF_GATEWAY, store.generatedKeys['ledger'],
                                                                    store.generatedKeys['notifications'],
                                                                    store.generatedKeys['mobile-app']] as String[])
        assertThat(page.locator('.next-steps code').last()).hasText('DevSecOps/PAYHUB/gateway-security')
        ownErrors().isEmpty()
    }

    def "leaving the wizard with answers asks before they are lost"() {
        when:
        open('/self-service')
        input(step(), 'Product name').fill('Trade Archive')
        menuLink('Pipeline Monitoring').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(currentStep()).hasText('Product')
        assertThat(input(step(), 'Product name')).hasValue('Trade Archive')

        when:
        menuLink('Pipeline Monitoring').click()
        dialogButton('Discard').click()
        page.waitForURL('**/monitoring')

        then:
        assertThat(page.locator('h1')).hasText('DevSecOps Pipeline Monitoring')
        ownErrors().isEmpty()
    }

    void openProduct(String department, String product) {
        open('/self-service')
        choose(step(), 'Department', department)
        radio(step(), 'A product in the portal').click()
        choose(step(), 'Product', product)
        assertThat(step().locator('dl.rows')).isVisible()
        button('Continue', true).click()
    }

    void addService(String name, String applicationId, String tool, String target = null) {
        button('Add a service', true).click()
        fillIn(dialog(), ['Service name': name, 'AppScan application ID': applicationId])
        dialogButton('Next').click()
        radio(dialog(), tool).click()
        if (target) {
            radio(dialog(), target).click()
        }
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

    Locator review(String term) {
        step().locator("dl.rows dt:text-is('${term}') + dd")
    }

    Locator serviceRow(String name) {
        holding(step().locator('.service-list li'), "strong:text-is('${name}')")
    }
}
