package com.bbh.itss.dso.portal.gui.performance

import java.nio.file.Files
import java.nio.file.Path

class BundleBreakdown {

    static final String INITIAL_FILE = /(?:src|href)="([^"]+\.(?:js|css))"/

    final List<Transfer> initial
    final List<Transfer> lazyScripts
    final List<Transfer> assets
    final List<Transfer> api

    private BundleBreakdown(List<Transfer> initial, List<Transfer> lazyScripts, List<Transfer> assets, List<Transfer> api) {
        this.initial = initial
        this.lazyScripts = lazyScripts
        this.assets = assets
        this.api = api
    }

    static BundleBreakdown of(Path indexHtml, List<Transfer> transfers) {
        def initialFiles = (Files.readString(indexHtml) =~ INITIAL_FILE).collect { match -> '/' + (match[1] as String) } as Set
        def document = transfers.first()
        def resources = transfers.drop(1)
        def api = resources.findAll { it.path.startsWith('/api/') }
        def files = resources - api
        def initial = [document] + files.findAll { it.path in initialFiles }
        def lazyScripts = files.findAll { !(it.path in initialFiles) && it.path.endsWith('.js') }
        new BundleBreakdown(initial, lazyScripts, files - initial - lazyScripts, api)
    }

    long getInitialBytes() {
        total(initial)
    }

    long getLazyScriptBytes() {
        total(lazyScripts)
    }

    long getAssetBytes() {
        total(assets)
    }

    long getApiBytes() {
        total(api)
    }

    private static long total(List<Transfer> transfers) {
        transfers.sum(0L) { it.transferred } as long
    }
}
