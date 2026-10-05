package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

import java.util.regex.Pattern

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

abstract class EditorSpecification extends GuiSpecification {

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
        assertThat(panel).hasClass(Pattern.compile('\\bmat-expanded\\b'))
        assertThat(openService()).hasCount(1)
    }

    void showSection(String label) {
        openService().locator(".rail-item:has(> span:text-is('${label}'))").click()
    }

    Locator input(Locator scope, String label) {
        scope.getByLabel(label, new Locator.GetByLabelOptions().setExact(true))
    }

    Locator formField(Locator scope, String label) {
        holding(scope.locator('mat-form-field'), "mat-label:text-is('${label}')")
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

    Locator errorOf(Locator scope, String label) {
        formField(scope, label).locator('mat-error')
    }

    Locator hintOf(Locator scope, String label) {
        formField(scope, label).locator('mat-hint')
    }

    void choose(Locator scope, String label, String option) {
        scope.getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName(label).setExact(true)).click()
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(option).setExact(true)).click()
    }

    Locator checkbox(Locator scope, String label) {
        scope.getByRole(AriaRole.CHECKBOX, new Locator.GetByRoleOptions().setName(label))
    }

    Locator select(Locator scope, String label) {
        scope.getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    void toggle(Locator scope, String label) {
        holdingText(scope.locator('mat-button-toggle'), label).locator('button').click()
    }

    Locator saveError() {
        page.locator('.save-bar .save-error')
    }
}
