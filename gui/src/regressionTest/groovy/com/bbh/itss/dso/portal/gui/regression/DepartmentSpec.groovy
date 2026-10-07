package com.bbh.itss.dso.portal.gui.regression

import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class DepartmentSpec extends ProductPageSpecification {

    def "departments are added and renamed, and a name already in use is refused with the portal's message"() {
        given:
        open('/products')

        when:
        button('Add department', true).click()
        dialogButton('Save').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Add department')
        assertThat(errorOf(dialog(), 'Name')).hasText('Required')
        api.requests('POST', '/api/departments').isEmpty()

        when:
        input(dialog(), 'Name').fill('  Treasury ')
        dialogButton('Save').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Treasury added')
        awaitRequest('POST', '/api/departments').json() == [name: 'Treasury']
        assertThat(departmentNames()).hasText((DEPARTMENTS + 'Treasury') as String[])
        assertThat(department('Treasury').locator('.tally')).hasText('0 DevSecOps pipelines for 0 products')
        assertThat(page.locator('.toolbar .count')).hasText('2 products in 6 departments')

        when:
        api.respond('PUT', '/api/departments/6', problem(409, 'Conflict', 'A department named Custody already exists'))
        buttonIn(department('Treasury'), 'Rename').click()
        input(dialog(), 'Name').fill('custody')
        dialogButton('Save').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Rename Treasury')
        assertThat(dialog().locator('[role=alert]')).hasText('A department named Custody already exists')
        awaitRequest('PUT', '/api/departments/6').json() == [name: 'custody', version: 0]

        when:
        dialogButton('Cancel').click()
        buttonIn(department('Capital Partners'), 'Rename').click()

        then:
        assertThat(input(dialog(), 'Name')).hasValue('Capital Partners')

        when:
        input(dialog(), 'Name').fill('Capital Partners Group')
        dialogButton('Save').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Capital Partners renamed to Capital Partners Group')
        awaitRequest('PUT', '/api/departments/2').json() == [name: 'Capital Partners Group', version: 0]
        assertThat(departmentNames()).hasText(['AI Lab', 'Capital Partners Group', 'Corporate Technology', 'Custody',
                                               'Fund Services', 'Treasury'] as String[])
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "only a department without products is deleted, once confirmed"() {
        given:
        open('/products')

        expect:
        assertThat(buttonIn(department('Fund Services'), 'Delete')).isDisabled()
        assertThat(department('Fund Services').locator('.delete'))
                .hasAttribute('title', 'Fund Services still has 1 product. Move them to another department first.')

        when:
        buttonIn(department('Custody'), 'Delete').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Delete Custody?')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('DELETE', '/api/departments/\\d+').isEmpty()

        when:
        buttonIn(department('Custody'), 'Delete').click()
        dialogButton('Delete department').click()

        then:
        assertThat(snackBar()).containsText('Custody deleted')
        assertThat(departmentNames()).hasText((DEPARTMENTS - 'Custody') as String[])
        awaitRequest('DELETE', '/api/departments/4')

        when:
        api.respond('DELETE', '/api/departments/1', problem(409, 'Conflict',
                'AI Lab still has 1 product(s). Move them to another department first.'))
        buttonIn(department('AI Lab'), 'Delete').click()
        dialogButton('Delete department').click()

        then:
        assertThat(snackBar()).containsText('AI Lab still has 1 product(s). Move them to another department first.')
        assertThat(departmentNames()).hasCount(4)
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }
}
