package com.bbh.itss.dso.portal.gui.performance

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

class PerformanceReport {

    static final Map<String, String> NAVIGATION_LABELS = [timeToFirstByte     : 'Time to first byte',
                                                          domContentLoaded    : 'DOMContentLoaded',
                                                          load                : 'Load event',
                                                          firstContentfulPaint: 'First contentful paint']

    private final List<Samples> scenarios = []
    private final Map<String, Samples> navigation = [:]
    private final List<String> environment = []
    private BundleBreakdown bundle
    private long bundleLimit
    private Map<String, Integer> payloads = [:]

    void describe(String line) {
        environment << line
    }

    void add(Samples samples) {
        scenarios << samples
    }

    void navigation(String key, double millis) {
        navigation.computeIfAbsent(key) { new Samples(key, '', 0) } << millis
    }

    void bundle(BundleBreakdown breakdown, long limitBytes) {
        bundle = breakdown
        bundleLimit = limitBytes
    }

    void payloads(Map<String, Integer> sizes) {
        payloads = sizes
    }

    void write(Path target) {
        Files.createDirectories(target.parent)
        Files.writeString(target, render())
    }

    String render() {
        def lines = ['# Gui performance report', '',
                     "Generated ${Instant.now().truncatedTo(ChronoUnit.SECONDS)} by the gui performance suite " +
                             '(`./gradlew :gui:performanceTest`) against the production build of the gui.', '']
        lines.addAll(environment.collect { "- $it".toString() })
        lines.addAll(['', '## Timings', '',
                      'Each time runs from the click (or from the start of the navigation) until the condition is ' +
                              'met in the DOM, measured in the browser with `performance.now()`. ' +
                              'The p95 uses the nearest rank; a scenario passes when its p95 is within the limit.', '',
                      '| Scenario | Ready when | Runs | Median | p95 | Max | Limit | Result |',
                      '| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |'])
        scenarios.each { samples ->
            lines << "| ${samples.scenario} | ${samples.readyWhen} | ${samples.values.size()} | ${ms(samples.median)} | " +
                    "${ms(samples.p95)} | ${ms(samples.max)} | ${ms(samples.limitMillis)} | ${samples.withinLimit ? 'pass' : 'FAIL'} |"
        }
        if (navigation) {
            lines.addAll(['', '## Navigation timing of the cold product list', '',
                          'From the Navigation Timing and Paint Timing APIs of the same cold loads.', '',
                          '| Metric | Median | p95 | Max |', '| --- | ---: | ---: | ---: |'])
            NAVIGATION_LABELS.each { key, label ->
                def samples = navigation[key]
                if (samples) {
                    lines << "| ${label} | ${ms(samples.median)} | ${ms(samples.p95)} | ${ms(samples.max)} |".toString()
                }
            }
        }
        if (bundle) {
            lines.addAll(['', '## Transferred size of the first page', '',
                          'Bytes on the wire for a cold load of `/products` (the stub server sends no compression, ' +
                                  'so a compressing proxy sends less). The initial bundle is the document with every ' +
                                  'script and stylesheet `index.html` references; its limit is the initial budget ' +
                                  'warning of `angular.json`.', '',
                          '| Part | Files | Transferred | Limit |', '| --- | ---: | ---: | ---: |',
                          "| Initial bundle | ${bundle.initial.size()} | ${kib(bundle.initialBytes)} | ${kib(bundleLimit)} |".toString(),
                          "| Lazy route chunks | ${bundle.lazyScripts.size()} | ${kib(bundle.lazyScriptBytes)} | |".toString(),
                          "| Fonts and other assets | ${bundle.assets.size()} | ${kib(bundle.assetBytes)} | |".toString(),
                          "| API responses | ${bundle.api.size()} | ${kib(bundle.apiBytes)} | |".toString()])
        }
        if (payloads) {
            lines.addAll(['', '## API payloads of the catalogue', '', '| Response | Size |', '| --- | ---: |'])
            payloads.each { path, size -> lines << "| `GET ${path}` | ${kib(size)} |".toString() }
        }
        lines.join('\n') + '\n'
    }

    private static String ms(double millis) {
        "${Math.round(millis)} ms"
    }

    private static String kib(long bytes) {
        String.format(Locale.ROOT, '%.1f KiB', bytes / 1024d)
    }
}
