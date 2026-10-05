package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

abstract class ProductPageSpecification extends GuiSpecification {

    Locator serviceCard(String name) {
        holding(page.locator('section.service'), "h2:text-is('${name}')")
    }

    Locator pipelineRow(String service, String type) {
        holding(serviceCard(service).locator('.pipeline'), ".pipeline-title strong:text-is('${type} pipeline')")
    }

    Locator pipelineTypes(String service) {
        serviceCard(service).locator('.pipeline-title strong')
    }

    Locator pipelineButton(String service, String type, String name) {
        buttonIn(pipelineRow(service, type), name)
    }

    Locator keyOf(String service, String type) {
        pipelineRow(service, type).locator('.key-value')
    }

    void pipelineAction(String service, String type, String action) {
        buttonIn(pipelineRow(service, type), 'More actions of the', false).click()
        menuItem(action).click()
    }

    Locator menuItem(String name) {
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(name).setExact(true))
    }

    Locator dialogSelect(String label) {
        dialog().getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName(label))
    }

    Locator dialogInput(String label) {
        dialog().getByLabel(label, new Locator.GetByLabelOptions().setExact(true))
    }

    Locator dialogError(String label) {
        holding(dialog().locator('mat-form-field'), "mat-label:text-is('${label}')").locator('mat-error')
    }

    void keyRows(List<List<String>> expected) {
        expected.eachWithIndex { parts, index ->
            parts.each { assertThat(dialog().locator('tr.mat-mdc-row').nth(index)).containsText(it) }
        }
    }
}
