package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.GuiSpecification
import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

abstract class EditorSpecification extends GuiSpecification {

    void startProduct(String name, String department = 'Corporate Technology') {
        open('/admin/products/new')
        choose(dialog(), 'Department', department)
        input(dialog(), 'Product name').fill(name)
        dialogButton('Continue').click()
        assertThat(dialog()).hasCount(0)
        assertThat(input(productFields(), 'Name')).hasValue(name)
    }

    Locator productFields() {
        page.locator('.product-fields')
    }

    Locator openService() {
        page.locator('dso-panel.expanded')
    }

    Locator servicePanel(String name) {
        holding(page.locator('dso-panel'), ".service-name:text-is('${name}')")
    }

    Locator serviceNames() {
        page.locator('dso-panel .service-name')
    }

    void expandService(String name) {
        expand(servicePanel(name))
    }

    void expandServiceAt(int index) {
        expand(page.locator('dso-panel').nth(index))
    }

    private void expand(Locator panel) {
        panel.locator('.accordion-header button').click()
        assertThat(panel).hasClass(~/\bexpanded\b/)
        assertThat(openService()).hasCount(1)
    }

    void showSection(String label) {
        openService().locator(".rail-item:has(> span:text-is('${label}'))").click()
        assertThat(openService().locator('.pane-header h3')).hasText(label)
    }

    void hasValues(Locator scope, Map<String, String> expected) {
        expected.each { label, value -> assertThat(input(scope, label)).hasValue(value) }
    }

    void fillIn(Locator scope, Map<String, String> values) {
        values.each { label, value -> input(scope, label).fill(value) }
    }

    void hasErrors(Locator scope, Map<String, String> expected) {
        expected.each { label, message -> assertThat(errorOf(scope, label)).hasText(message) }
    }

    Locator hintOf(Locator scope, String label) {
        formField(scope, label).locator('dso-hint')
    }

    void toggle(Locator scope, String label) {
        holdingText(scope.locator('dso-toggle-group button'), label).click()
    }

    Locator selected(Locator scope, String label) {
        select(scope, label).locator('option:checked')
    }

    Locator saveError() {
        page.locator('.save-bar .save-error')
    }

    Locator templateCard(String title) {
        page.locator("section.template-card[aria-label='${title}']")
    }

    Locator account(Locator scope, int index) {
        scope.locator('fieldset.account').nth(index)
    }

    Locator found() {
        dialog().locator('.results .value')
    }

    void lookUp(Locator scope, String label, String search, String value) {
        buttonIn(scope, "Find $label").click()
        assertThat(dialog().locator('h2')).hasText("Find $label")
        input(dialog(), 'Search').fill(search)
        assertThat(found().first()).isVisible()
        holding(dialog().locator('.results button'), ".value:text-is('${value}')").click()
        assertThat(dialog()).hasCount(0)
    }
}
