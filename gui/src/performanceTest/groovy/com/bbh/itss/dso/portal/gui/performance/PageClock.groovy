package com.bbh.itss.dso.portal.gui.performance

import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import groovy.json.JsonOutput

class PageClock {

    static final String SCRIPT = '''
        (() => {
          const ready = (condition) => {
            const found = document.querySelectorAll(condition.selector);
            if (found.length < condition.count) {
              return false;
            }
            return !condition.visible || found[condition.count - 1].checkVisibility({
              opacityProperty: true,
              visibilityProperty: true,
            });
          };
          const until = (condition, timeout) => new Promise((resolve, reject) => {
            const observer = new MutationObserver(() => test());
            const timer = setTimeout(() => {
              observer.disconnect();
              reject(new Error(`${condition.selector} did not reach ${condition.count} within ${timeout} ms`));
            }, timeout);
            const test = () => {
              if (ready(condition)) {
                observer.disconnect();
                clearTimeout(timer);
                resolve(performance.now());
              }
            };
            observer.observe(document, { childList: true, subtree: true, attributes: true, characterData: true });
            test();
          });
          const milestones = {};
          const watched = MILESTONES;
          const record = () => {
            for (const [name, condition] of Object.entries(watched)) {
              if (milestones[name] === undefined && document.documentElement && ready(condition)) {
                milestones[name] = performance.now();
              }
            }
          };
          new MutationObserver(record).observe(document, { childList: true, subtree: true });
          window.dsoClock = { ready, until, milestones };
        })();
    '''

    static final String CLICK = '''
        async (element, { condition, timeout }) => {
          if (window.dsoClock.ready(condition)) {
            throw new Error(`${condition.selector} was ready before the click`);
          }
          const start = performance.now();
          element.click();
          const end = await window.dsoClock.until(condition, timeout);
          return end - start;
        }
    '''

    static final String NAVIGATION = '''
        () => {
          const navigation = performance.getEntriesByType('navigation')[0];
          const paint = performance.getEntriesByName('first-contentful-paint')[0];
          return {
            timeToFirstByte: navigation.responseStart,
            domContentLoaded: navigation.domContentLoadedEventEnd,
            load: navigation.loadEventEnd,
            firstContentfulPaint: paint ? paint.startTime : null,
          };
        }
    '''

    static final String TRANSFERS = '''
        () => {
          const navigation = performance.getEntriesByType('navigation')[0];
          return [navigation, ...performance.getEntriesByType('resource')].map((entry) => ({
            url: entry.name,
            transferred: entry.transferSize,
            encoded: entry.encodedBodySize,
          }));
        }
    '''

    static final int TIMEOUT_MILLIS = 60000

    private final Page page

    PageClock(Page page) {
        this.page = page
    }

    static void install(BrowserContext context, Map<String, Readiness> milestones) {
        def watched = JsonOutput.toJson(milestones.collectEntries { name, readiness -> [(name): readiness.toMap()] })
        context.addInitScript(SCRIPT.replace('MILESTONES', watched))
    }

    double clickUntil(Locator target, Readiness readiness) {
        target.evaluate(CLICK, [condition: readiness.toMap(), timeout: TIMEOUT_MILLIS]) as double
    }

    double milestone(String name) {
        page.waitForFunction("name => window.dsoClock && window.dsoClock.milestones[name] !== undefined", name,
                new Page.WaitForFunctionOptions().setTimeout(TIMEOUT_MILLIS))
        page.evaluate('name => window.dsoClock.milestones[name]', name) as double
    }

    Map<String, Double> navigationTimings() {
        (page.evaluate(NAVIGATION) as Map).collectEntries { key, value -> [(key): value == null ? null : value as double] }
    }

    List<Transfer> transfers() {
        (page.evaluate(TRANSFERS) as List<Map>).collect { entry ->
            new Transfer(URI.create(entry.url as String).path, entry.transferred as long, entry.encoded as long)
        }
    }
}
