package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

abstract class ProductPageSpecification extends GuiSpecification {

    Locator serviceCard(String name) {
        page.locator('section.service').filter(new Locator.FilterOptions().setHas(page.locator("h2:text-is('${name}')")))
    }

    Locator pipelineRow(String service, String type) {
        serviceCard(service).locator('.pipeline').filter(new Locator.FilterOptions()
                .setHas(page.locator(".pipeline-title strong:text-is('${type} pipeline')")))
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

    Locator dialogInput(String label) {
        dialog().getByLabel(label, new Locator.GetByLabelOptions().setExact(true))
    }

    Locator dialogError(String label) {
        dialog().locator('mat-form-field').filter(new Locator.FilterOptions()
                .setHas(page.locator("mat-label:text-is('${label}')"))).locator('mat-error')
    }
}
