package com.bbh.itss.dso.portal.gui.performance

import com.bbh.itss.dso.portal.gui.support.GuiSpecification
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.ReducedMotion
import spock.lang.Shared

import java.nio.file.Paths

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class GuiPerformanceSpec extends GuiSpecification {

    static final int WARMUPS = 1
    static final int RUNS = 5
    static final long INITIAL_BUNDLE_LIMIT = 1024 * 1024
    static final Map<String, Double> LIMITS = [coldList: 2500d, detail: 1500d, editor: 2000d, expand: 500d, overview: 1000d,
                                               productMonitoring: 1000d, evidence: 1500d]
    static final Readiness PRODUCT_ROWS = Readiness.rendered('tr.mat-mdc-row', LargeCatalogue.PRODUCTS)
    static final Map<String, Readiness> MILESTONES = [productList: PRODUCT_ROWS]

    @Shared
    LargeCatalogue catalogue = LargeCatalogue.generate()

    @Shared
    PerformanceReport report = new PerformanceReport()

    def setupSpec() {
        report.describe("Catalogue: ${LargeCatalogue.PRODUCTS} products with ${LargeCatalogue.SERVICES_PER_PRODUCT} services " +
                "each (${catalogue.serviceCount} services) and ${LargeCatalogue.PIPELINE_TYPES.size()} pipelines per " +
                "service (${catalogue.pipelineCount} pipelines), with monitoring and change evidence for every product.")
        report.describe("Browser: Chromium ${browser.version()}, viewport ${WIDTH}×${HEIGHT}, reduced motion " +
                '(no Material animations), gui and stub API served from 127.0.0.1.')
        report.describe("Runs: ${WARMUPS} warm-up run (not counted) and ${RUNS} measured runs per scenario; " +
                "every product page run opens another product.")
        report.describe("Time limits are scaled by -Dperformance.factor (now ${performanceFactor()}).")
    }

    def setup() {
        catalogue.serve(api)
        PageClock.install(context, MILESTONES)
    }

    def cleanupSpec() {
        report.payloads(catalogue.payloadSizes)
        report.write(Paths.get(System.getProperty('performance.report', 'build/reports/performance/gui-performance-report.md')))
    }

    @Override
    Browser.NewContextOptions contextOptions() {
        super.contextOptions().setReducedMotion(ReducedMotion.REDUCE)
    }

    def "the product list of every product renders on a cold start, within its limit and the initial bundle budget"() {
        given:
        def samples = new Samples('Product list, cold start', PRODUCT_ROWS.describe(), limit('coldList'))
        BundleBreakdown bundle = null

        when:
        (WARMUPS + RUNS).times { int run ->
            def cold = newContext()
            try {
                PageClock.install(cold, MILESTONES)
                def coldPage = cold.newPage()
                watchErrors(coldPage)
                coldPage.navigate(url('/products'))
                def clock = new PageClock(coldPage)
                def rendered = clock.milestone('productList')
                coldPage.waitForLoadState(LoadState.NETWORKIDLE)
                if (run >= WARMUPS) {
                    samples << rendered
                    clock.navigationTimings().findAll { it.value != null }.each { key, value -> report.navigation(key, value) }
                    bundle = bundle ?: BundleBreakdown.of(distDir().resolve('index.html'), clock.transfers())
                }
            } finally {
                cold.close()
            }
        }
        report.add(samples)
        report.bundle(bundle, INITIAL_BUNDLE_LIMIT)

        then:
        samples.withinLimit
        bundle.initialBytes <= INITIAL_BUNDLE_LIMIT
        bundle.initial*.path.any { it ==~ '/main-.+\\.js' }
        bundle.api*.path.contains('/api/products')
        ownErrors().isEmpty()
    }

    def "a product with every pipeline opens from the product list"() {
        given:
        def ready = Readiness.rendered('section.service .pipeline', catalogue.pipelinesPerProduct)
        def samples = new Samples('Product page from the product list', ready.describe(), limit('detail'))

        when:
        measure(samples) { int run ->
            open('/products')
            assertThat(page.locator(PRODUCT_ROWS.selector)).hasCount(PRODUCT_ROWS.count)
            clock().clickUntil(page.locator("a.name[href='/products/${productOf(run)}']"), ready)
        }

        then:
        samples.withinLimit
        assertThat(page.locator('h1')).hasText(catalogue.productName(productOf(WARMUPS + RUNS - 1)))
        assertThat(page.locator('section.service')).hasCount(LargeCatalogue.SERVICES_PER_PRODUCT)
        ownErrors().isEmpty()
    }

    def "the editor of a product opens from its page and expands a service"() {
        given:
        def editorReady = Readiness.rendered('mat-expansion-panel dso-service-fields', LargeCatalogue.SERVICES_PER_PRODUCT)
        def serviceReady = Readiness.shown('mat-expansion-panel.mat-expanded dso-service-fields input')
        def opening = new Samples('Product editor from the product page', editorReady.describe(), limit('editor'))
        def expanding = new Samples('Expanding a service in the editor', serviceReady.describe(), limit('expand'))
        def middle = LargeCatalogue.SERVICES_PER_PRODUCT.intdiv(2)

        when:
        (WARMUPS + RUNS).times { int run ->
            def id = productOf(run)
            open("/products/$id")
            assertThat(page.locator('section.service .pipeline')).hasCount(catalogue.pipelinesPerProduct)
            def opened = clock().clickUntil(page.locator("a[href='/products/$id/edit']"), editorReady)
            def expanded = clock().clickUntil(page.locator('mat-expansion-panel-header').nth(middle), serviceReady)
            if (run >= WARMUPS) {
                opening << opened
                expanding << expanded
            }
        }
        report.add(opening)
        report.add(expanding)

        then:
        opening.withinLimit
        expanding.withinLimit
        assertThat(page.locator('mat-expansion-panel.mat-expanded .service-name')).hasText(catalogue.serviceName(middle))
        ownErrors().isEmpty()
    }

    def "the monitoring overview and a product's monitoring open from the menu"() {
        given:
        def overviewReady = Readiness.rendered('a.card.product', LargeCatalogue.PRODUCTS)
        def productReady = Readiness.rendered('a.pipeline-link', catalogue.pipelinesPerProduct)
        def overview = new Samples('Monitoring overview from the menu', overviewReady.describe(), limit('overview'))
        def product = new Samples("A product's monitoring from the overview", productReady.describe(), limit('productMonitoring'))

        when:
        (WARMUPS + RUNS).times { int run ->
            open('/products')
            assertThat(page.locator(PRODUCT_ROWS.selector)).hasCount(PRODUCT_ROWS.count)
            def overviewTime = clock().clickUntil(menuLink('Pipeline Monitoring'), overviewReady)
            def productTime = clock().clickUntil(page.locator("a.card.product[href='/monitoring/products/${productOf(run)}']"), productReady)
            if (run >= WARMUPS) {
                overview << overviewTime
                product << productTime
            }
        }
        report.add(overview)
        report.add(product)

        then:
        overview.withinLimit
        product.withinLimit
        assertThat(page.locator('h1')).hasText(catalogue.productName(productOf(WARMUPS + RUNS - 1)))
        ownErrors().isEmpty()
    }

    def "a product's change evidence expands with every pipeline"() {
        given:
        def ready = Readiness.rendered('dso-pipeline-evidence-card', catalogue.pipelinesPerProduct)
        def samples = new Samples("A product's change evidence expanded", ready.describe(), limit('evidence'))

        when:
        measure(samples) { int run ->
            open('/evidence')
            assertThat(page.locator('mat-expansion-panel')).hasCount(LargeCatalogue.PRODUCTS)
            clock().clickUntil(evidenceHeader(productOf(run)), ready)
        }

        then:
        samples.withinLimit
        api.requests('GET', '/api/evidence/products/\\d+')*.path ==
                (0..<WARMUPS + RUNS).collect { "/api/evidence/products/${productOf(it)}".toString() }
        ownErrors().isEmpty()
    }

    void measure(Samples samples, Closure<Double> run) {
        (WARMUPS + RUNS).times { int index ->
            def millis = run.call(index)
            if (index >= WARMUPS) {
                samples << millis
            }
        }
        report.add(samples)
    }

    PageClock clock() {
        new PageClock(page)
    }

    Locator evidenceHeader(int productId) {
        page.locator('mat-expansion-panel').filter(new Locator.FilterOptions()
                .setHas(page.locator(".code:text-is('${catalogue.code(productId)}')")))
                .locator('mat-expansion-panel-header')
    }

    static int productOf(int run) {
        1 + (run * 7) % LargeCatalogue.PRODUCTS
    }

    static double limit(String scenario) {
        LIMITS[scenario] * performanceFactor()
    }
}
