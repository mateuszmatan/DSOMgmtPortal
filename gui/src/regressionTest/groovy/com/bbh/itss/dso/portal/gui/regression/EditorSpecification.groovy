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
        page.locator('mat-expansion-panel').filter(new Locator.FilterOptions()
                .setHas(page.locator(".service-name:text-is('${name}')")))
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
        scope.locator('mat-form-field').filter(new Locator.FilterOptions()
                .setHas(page.locator("mat-label:text-is('${label}')")))
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

    Locator select(Locator scope, String label) {
        scope.getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    void toggle(Locator scope, String label) {
        scope.locator('mat-button-toggle').filter(new Locator.FilterOptions().setHasText(label)).locator('button').click()
    }

    Locator saveError() {
        page.locator('.save-bar .save-error')
    }
}
