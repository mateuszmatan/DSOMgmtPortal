package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
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
        page.locator('mat-expansion-panel.mat-expanded')
    }

    Locator servicePanel(String name) {
        holding(page.locator('mat-expansion-panel'), ".service-name:text-is('${name}')")
    }

    Locator serviceNames() {
        page.locator('mat-expansion-panel .service-name')
    }

    void expandService(String name) {
        expand(servicePanel(name))
    }

    void expandServiceAt(int index) {
        expand(page.locator('mat-expansion-panel').nth(index))
    }

    private void expand(Locator panel) {
        panel.locator('mat-expansion-panel-header').click()
        assertThat(panel).hasClass(~/\bmat-expanded\b/)
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
        formField(scope, label).locator('mat-hint')
    }

    void toggle(Locator scope, String label) {
        holdingText(scope.locator('mat-button-toggle'), label).locator('button').click()
    }

    Locator saveError() {
        page.locator('.save-bar .save-error')
    }
}
