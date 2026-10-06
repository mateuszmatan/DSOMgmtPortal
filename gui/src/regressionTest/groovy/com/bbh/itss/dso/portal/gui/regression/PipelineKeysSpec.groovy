package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.ApiData
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.bbh.itss.dso.portal.gui.support.StubResponse
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

import java.util.regex.Pattern

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class PipelineKeysSpec extends ProductPageSpecification {

    static final String GUI_FULL_KEY = '7b62170e-5c42-4bec-8cbc-035977e3299e'
    static final String REGENERATED_KEY = '3f9d2c4e-8a1b-4c7d-9e2f-5b6a7c8d1e04'
    static final Pattern KEY_VALUE = ~/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/

    def "keys show by their hint until shown, and are copied whole"() {
        given:
        recordClipboard()
        open('/products/1')

        expect:
        assertThat(keyOf('gui', 'Full')).hasText('7b62170e…299e')
        assertThat(keyOf('gui', 'SAST scanning')).hasText('2c0ca4f4…e713')
        !page.locator('main').textContent().contains(GUI_FULL_KEY)

        when:
        pipelineButton('gui', 'Full', 'Show the key of the full pipeline').click()

        then:
        assertThat(keyOf('gui', 'Full')).hasText(GUI_FULL_KEY)
        assertThat(keyOf('gui', 'SAST scanning')).hasText('2c0ca4f4…e713')

        when:
        pipelineButton('gui', 'Full', 'Hide the key of the full pipeline').click()
        pipelineButton('gui', 'Full', 'Copy the key of the full pipeline').click()

        then:
        assertThat(keyOf('gui', 'Full')).hasText('7b62170e…299e')
        assertThat(snackBar()).containsText('Key copied to the clipboard')
        copiedTexts() == [GUI_FULL_KEY]
        ownErrors().isEmpty()
    }

    def "the key history lists every key by its hint, with the reason of each invalidation"() {
        given:
        open('/products/2')

        when:
        pipelineAction('mobile-app', 'SAST scanning', 'Key history')

        then:
        assertThat(dialog().locator('h2')).hasText('Key history')
        assertThat(dialog().locator('.intro')).hasText('Sast pipeline of mobile-app in Payments Hub.')
        assertThat(dialog().locator('tr.mat-mdc-row td').first()).hasText('dd3ac7a4…825e')
        assertThat(dialog().locator('tr.mat-mdc-row')).containsText(['Invalidated'] as String[])
        assertThat(dialog().locator('.reason')).hasText('Mobile app moved to the new mobile platform pipeline')
        assertThat(dialog().locator('.key-status')).containsText('Key invalidated')
        assertThat(dialogButton('Regenerate key')).isVisible()
        !(dialog().textContent() =~ KEY_VALUE)
        api.requests('GET', '/api/pipelines/9').size() == 1
        ownErrors().isEmpty()
    }

    def "a pipeline is added to a service with the types it lacks, after its field errors are fixed"() {
        given:
        def created = ApiData.pipeline(id: 20, productId: 1, serviceId: 1, serviceName: 'gui', type: 'SECURITY',
                entryPoint: 'devSecOpsSecurityPipeline', agentLabels: ['linux-agent', 'docker'],
                extendedPipelineJob: 'DevSecOps/CERTSCANNER/gui-extended',
                jenkinsJob: 'DevSecOps/CERTSCANNER/gui-security-scan',
                jenkinsJobUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERTSCANNER/job/gui-security-scan/',
                description: 'Nightly security scan', activeKey: ApiData.activeKey(30, ApiData.keyValue(30)),
                influxProjectTag: 'CERTSCANNER-guisecurity')
        api.respond('POST', '/api/services/1/pipelines', StubResponse.problem(400, 'Bad Request', 'Some values are not valid',
                [errors: [[field: 'jenkinsJob', message: 'another pipeline already runs in this Jenkins job']]]))
        open('/products/1')

        when:
        buttonIn(serviceCard('gui'), 'Add pipeline', false).click()
        dialogSelect('Pipeline type').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Add pipeline')
        assertThat(page.getByRole(AriaRole.OPTION)).hasText(['Security', 'Extended'] as String[])

        when:
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName('Security')).click()
        dialogInput('Jenkins agent labels').fill('linux-agent, docker')
        dialogInput('Jenkins job').fill('DevSecOps/CERTSCANNER/gui-security')
        dialogInput('Extended pipeline job').fill('DevSecOps/../gui-extended')
        dialogInput('Description').fill('Nightly security scan')
        dialogButton('Add pipeline').click()

        then:
        assertThat(dialogError('Extended pipeline job')).hasText("A job path such as DevSecOps/CERT/backend-api-extended, without '..'")
        api.requests('POST', '/api/services/1/pipelines').isEmpty()

        when:
        dialogInput('Extended pipeline job').fill('DevSecOps/CERTSCANNER/gui-extended')
        dialogButton('Add pipeline').click()

        then:
        assertThat(dialogError('Jenkins job')).hasText('another pipeline already runs in this Jenkins job')
        assertThat(dialog()).isVisible()

        when:
        api.respond('POST', '/api/services/1/pipelines', created, 201)
        dialogInput('Jenkins job').fill('DevSecOps/CERTSCANNER/gui-security-scan')
        dialogButton('Add pipeline').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('POST', '/api/services/1/pipelines').size() == 2
        api.lastRequest('POST', '/api/services/1/pipelines').json() == [
                type               : 'SECURITY', agentLabels: ['linux-agent', 'docker'],
                extendedPipelineJob: 'DevSecOps/CERTSCANNER/gui-extended', securityPipelineJob: null,
                jenkinsJob         : 'DevSecOps/CERTSCANNER/gui-security-scan', description: 'Nightly security scan']
        assertThat(pipelineTypes('gui')).hasText(['Full pipeline', 'Security pipeline', 'SAST scanning pipeline'] as String[])
        assertThat(keyOf('gui', 'Security')).hasText(ApiData.hint(ApiData.keyValue(30)))
        assertThat(snackBar()).containsText('Security pipeline added to gui')
        assertThat(stat('Pipelines')).hasText('4')
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "replacing a key asks first, issues a new key and shows it once"() {
        given:
        def replaced = StubApi.fixture('pipeline-1.json') as Map
        def value = ApiData.keyValue(41)
        def key = ApiData.activeKey(41, value)
        replaced += [activeKey: key, keys: [key, ApiData.revokedKey(replaced.activeKey as Map, 'Replaced by a new key', ApiData.ISSUED_AT)]]
        api.respond('POST', '/api/pipelines/1/keys', replaced)
        open('/products/1')

        when:
        pipelineAction('gui', 'Full', 'Replace key')

        then:
        assertThat(dialog().locator('h2')).hasText('Replace the key?')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('POST', '/api/pipelines/1/keys').isEmpty()

        when:
        pipelineAction('gui', 'Full', 'Replace key')
        dialogButton('Replace key').click()

        then:
        assertThat(keyOf('gui', 'Full')).hasText(value)
        assertThat(snackBar()).containsText('New key issued')
        awaitRequest('POST', '/api/pipelines/1/keys').json() == [:]
        assertThat(stat('Active keys')).hasText('3')
        ownErrors().isEmpty()
    }

    def "a key invalidated with a reason is regenerated from its history, and the old key stays invalidated"() {
        given:
        def original = StubApi.fixture('pipeline-2.json') as Map
        def oldKey = original.activeKey as Map
        def revoked = original + [enabled: false, activeKey: null, keys: null]
        def history = revoked + [keys: [ApiData.revokedKey(oldKey, 'Key printed in a build log', '2026-10-05T09:30:00Z')]]
        def newValue = ApiData.keyValue(42)
        def newKey = ApiData.activeKey(42, newValue, '2026-10-05T09:45:00Z')
        def regenerated = original + [enabled: true, activeKey: newKey, keys: [newKey] + (history.keys as List)]
        api.respond('POST', '/api/pipelines/2/keys/revoke', revoked)
        api.respond('GET', '/api/pipelines/2', history)
        api.respond('POST', '/api/pipelines/2/keys', regenerated)
        open('/products/1')

        when:
        pipelineAction('gui', 'SAST scanning', 'Invalidate key')

        then:
        assertThat(dialog().locator('h2')).hasText('Invalidate the pipeline key?')
        assertThat(dialog().locator('.banner')).containsText('The sast pipeline of gui stops working')

        when:
        dialogButton('Invalidate key').click()

        then:
        assertThat(dialogError('Reason')).hasText('Required')
        api.requests('POST', '/api/pipelines/2/keys/revoke').isEmpty()

        when:
        dialogInput('Reason').fill('  Key printed in a build log  ')
        dialogButton('Invalidate key').click()

        then:
        assertThat(dialog()).hasCount(0)
        awaitRequest('POST', '/api/pipelines/2/keys/revoke').json() == [reason: 'Key printed in a build log']
        assertThat(snackBar()).containsText('Key invalidated: the pipeline stops at its next start')
        assertThat(pipelineRow('gui', 'SAST scanning').locator('.key-state')).hasText('Key invalidated')
        assertThat(pipelineRow('gui', 'SAST scanning').locator('.revoked-note'))
                .hasText('The pipeline is refused its configuration until its key is regenerated.')
        assertThat(pipelineButton('gui', 'SAST scanning', 'Regenerate key of the SAST scanning pipeline')).isVisible()
        assertThat(stat('Invalidated keys')).hasText('1')

        when:
        pipelineAction('gui', 'SAST scanning', 'Key history')
        dialogButton('Regenerate key').click()

        then:
        assertThat(dialog().locator('.key-status')).containsText('New key')
        assertThat(dialog().locator('.key-status .key-value')).hasText(newValue)
        assertThat(dialog().locator('tr.mat-mdc-row')).hasCount(2)
        keyRows([[ApiData.hint(newValue), 'Active'],
                 ['2c0ca4f4…e713', 'Invalidated', 'Key printed in a build log']])
        awaitRequest('POST', '/api/pipelines/2/keys').json() == [:]

        when:
        dialogButton('Close').click()

        then:
        assertThat(keyOf('gui', 'SAST scanning')).hasText(newValue)
        assertThat(pipelineRow('gui', 'SAST scanning').locator('.key-state')).hasText('Key active')
        assertThat(button('Regenerate key')).hasCount(0)
        assertThat(stat('Invalidated keys')).hasText('0')
        api.requests('POST', '/api/pipelines/2/keys').size() == 1
        ownErrors().isEmpty()
    }

    def "an invalidated key is regenerated from the product page after a failed try, and the old key stays invalidated"() {
        given:
        def regenerated = StubApi.fixture('pipeline-9-regenerated.json') as Map
        api.respond('POST', '/api/pipelines/9/keys', StubResponse.problem(503, 'Service Unavailable', 'The key store is being upgraded; try again in a minute'))
        open('/products/2')
        def regenerate = pipelineButton('mobile-app', 'SAST scanning', 'Regenerate key of the SAST scanning pipeline')

        expect:
        assertThat(regenerate).hasText('Regenerate key')
        assertThat(regenerate.locator('mat-icon')).hasCount(0)
        assertThat(pipelineRow('mobile-app', 'SAST scanning').locator('.key-state')).hasText('Key invalidated')

        when:
        regenerate.click()

        then:
        assertThat(snackBar()).containsText('The key store is being upgraded; try again in a minute')
        assertThat(regenerate).isEnabled()
        assertThat(pipelineRow('mobile-app', 'SAST scanning').locator('.key-state')).hasText('Key invalidated')
        awaitRequest('POST', '/api/pipelines/9/keys').json() == [:]

        when:
        api.respond('POST', '/api/pipelines/9/keys', regenerated)
        api.respond('GET', '/api/pipelines/9', regenerated)
        regenerate.click()

        then:
        assertThat(keyOf('mobile-app', 'SAST scanning')).hasText(REGENERATED_KEY)
        assertThat(pipelineRow('mobile-app', 'SAST scanning').locator('.key-state')).hasText('Key active')
        assertThat(regenerate).hasCount(0)
        assertThat(stat('Invalidated keys')).hasText('0')
        awaitRequest('POST', '/api/pipelines/9/keys', 2).json() == [:]
        api.requests('POST', '/api/pipelines/9/keys/revoke').isEmpty()

        when:
        pipelineAction('mobile-app', 'SAST scanning', 'Key history')

        then:
        assertThat(dialog().locator('tr.mat-mdc-row')).hasCount(2)
        keyRows([[ApiData.hint(REGENERATED_KEY), 'Active'], ['dd3ac7a4…825e', 'Invalidated']])
        assertThat(dialog().locator('.reason')).hasText('Mobile app moved to the new mobile platform pipeline')
        assertThat(dialogButton('Regenerate key')).hasCount(0)
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    def "pipeline settings are edited with the type fixed"() {
        given:
        def updated = StubApi.fixture('pipeline-1.json') as Map
        updated += [agentLabels: ['linux-agent', 'docker'], description: 'Main branch delivery', keys: null]
        api.respond('PUT', '/api/pipelines/1', updated)
        open('/products/1')

        when:
        pipelineAction('gui', 'Full', 'Settings')

        then:
        assertThat(dialog().locator('h2')).hasText('Pipeline settings')
        assertThat(dialogSelect('Pipeline type')).isDisabled()
        assertThat(dialogInput('Jenkins job')).hasValue('DevSecOps/CERTSCANNER/gui-full')

        when:
        dialogInput('Jenkins agent labels').fill('linux-agent, docker')
        dialogInput('Description').fill('Main branch delivery')
        dialogButton('Save').click()

        then:
        assertThat(dialog()).hasCount(0)
        awaitRequest('PUT', '/api/pipelines/1').json() == [type      : 'FULL', agentLabels: ['linux-agent', 'docker'],
                                                               extendedPipelineJob: null, securityPipelineJob: null,
                                                               jenkinsJob: 'DevSecOps/CERTSCANNER/gui-full', description: 'Main branch delivery']
        assertThat(pipelineRow('gui', 'Full').locator('.pipeline-meta')).containsText('linux-agent, docker')
        assertThat(pipelineRow('gui', 'Full').locator('.pipeline-meta')).containsText('Main branch delivery')
        assertThat(snackBar()).containsText('Pipeline settings saved')
        ownErrors().isEmpty()
    }

    def "a pipeline and then the whole product are deleted after confirmation"() {
        given:
        api.respond('DELETE', '/api/pipelines/3', StubResponse.empty())
        api.respond('DELETE', '/api/products/1', StubResponse.empty())
        open('/products/1')

        when:
        pipelineAction('backend-api', 'Full', 'Delete pipeline')

        then:
        assertThat(dialog().locator('h2')).hasText('Delete the pipeline?')
        assertThat(dialog()).containsText('The full pipeline of backend-api and its key history are deleted.')

        when:
        dialogButton('Delete pipeline').click()

        then:
        assertThat(serviceCard('backend-api').locator('.no-pipelines')).hasText('No pipeline yet. Add one to give this service a DevSecOps key.')
        assertThat(snackBar()).containsText('Pipeline deleted')
        awaitRequest('DELETE', '/api/pipelines/3') != null

        when:
        button('Delete', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Delete CertScanner?')
        assertThat(dialog()).containsText('The product, its 2 services and 2 pipelines with their keys are deleted.')

        when:
        dialogButton('Delete product').click()
        page.waitForURL('**/products')

        then:
        awaitRequest('DELETE', '/api/products/1') != null
        assertThat(snackBar()).containsText('CertScanner deleted')
        ownErrors().isEmpty()
    }
}
