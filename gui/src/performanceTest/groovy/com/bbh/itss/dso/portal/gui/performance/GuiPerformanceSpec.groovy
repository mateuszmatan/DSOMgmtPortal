package com.bbh.itss.dso.portal.gui.performance

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.ReducedMotion
import com.microsoft.playwright.options.WaitUntilState
import spock.lang.Shared

import java.nio.file.Files
import java.nio.file.Paths

class GuiPerformanceSpec extends GuiSpecification {

    static final int WARMUPS = 1
    static final int RUNS = 5
    static final double FACTOR = (System.getProperty('performance.factor') ?: '1') as double
    static final String READY = '''({ selector, count }) => {
        const found = document.querySelectorAll(selector);
        const shown = found.length >= count && found[count - 1].checkVisibility({ opacityProperty: true, visibilityProperty: true });
        return shown && performance.now();
    }'''
    static final String CLICK = 'element => { const start = performance.now(); element.click(); return start; }'

    @Shared
    LargeCatalogue catalogue = new LargeCatalogue()

    @Shared
    List<String> report = ['# Gui performance report', '',
                           "${LargeCatalogue.PRODUCTS} products × ${LargeCatalogue.SERVICES} services × ${LargeCatalogue.TYPES.size()} pipelines; " +
                                   "${WARMUPS} warm-up and ${RUNS} measured runs per page; time from the click (or the navigation) until the " +
                                   "content is visible; limits × ${FACTOR} (-Dperformance.factor).", '',
                           '| Page | Visible when | Median | p95 | Limit | Result |', '| --- | --- | ---: | ---: | ---: | --- |']

    def setup() {
        catalogue.serve(api)
    }

    def cleanupSpec() {
        def target = Paths.get(System.getProperty('performance.report', 'build/reports/performance/gui-performance-report.md'))
        Files.createDirectories(target.parent)
        Files.writeString(target, report.join('\n') + '\n')
    }

    @Override
    Browser.NewContextOptions contextOptions() {
        super.contextOptions().setReducedMotion(ReducedMotion.REDUCE)
    }

    def "#scenario is visible within #limit ms at the 95th percentile"() {
        when:
        def times = (0..<WARMUPS + RUNS).collect { int run -> measure(from, click, 1 + (run * 7) % LargeCatalogue.PRODUCTS, [selector: selector, count: count]) }
        def sorted = times.drop(WARMUPS).sort()
        def p95 = sorted[(int) Math.ceil(0.95d * RUNS) - 1]
        report << "| $scenario | $count × `$selector` | ${Math.round(sorted[RUNS.intdiv(2)])} ms | ${Math.round(p95)} ms | ${Math.round(limit * FACTOR)} ms | ${p95 <= limit * FACTOR ? 'pass' : 'FAIL'} |".toString()

        then:
        p95 <= limit * FACTOR
        ownErrors().isEmpty()

        where:
        scenario                                   | limit | from                | click                                            | selector                                                    | count
        'Product list, cold start'                 | 2500  | null                | null                                             | 'tr.mat-mdc-row'                                            | LargeCatalogue.PRODUCTS
        'Product page from the product list'       | 1500  | '/products'         | "a.name[href='/products/ID']"                    | 'section.service .pipeline'                                 | LargeCatalogue.PIPELINES
        'Product editor from the product page'     | 2000  | '/products/ID'      | "a[href='/products/ID/edit']"                    | 'mat-expansion-panel-header .service-name'                  | LargeCatalogue.SERVICES
        'A service expanded in the editor'         | 500   | '/products/ID/edit' | 'mat-expansion-panel-header >> nth=8'            | 'mat-expansion-panel.mat-expanded dso-service-fields input' | 1
        'Monitoring overview from the menu'        | 1000  | '/products'         | "nav.menu a[href='/monitoring']"                 | 'a.card.product'                                            | LargeCatalogue.PRODUCTS
        "A product's monitoring from the overview" | 1000  | '/monitoring'       | "a.card.product[href='/monitoring/products/ID']" | 'a.pipeline-link'                                           | LargeCatalogue.PIPELINES
        "A product's change evidence expanded"     | 1500  | '/evidence'         | "mat-expansion-panel-header:has(.code:text-is('CATID'))" | 'dso-pipeline-evidence-card'                         | LargeCatalogue.PIPELINES
    }

    double measure(String from, String click, int product, Map ready) {
        if (from) {
            page.navigate(url(from.replace('ID', "$product")))
            assert !page.evaluate(READY, ready)
            def start = page.locator(click.replace('ID', "$product")).evaluate(CLICK) as double
            return (page.waitForFunction(READY, ready).jsonValue() as double) - start
        }
        context.close()
        context = newContext()
        page = context.newPage()
        watchErrors(page)
        page.navigate(url('/products'), new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT))
        page.waitForFunction(READY, ready).jsonValue() as double
    }
}
