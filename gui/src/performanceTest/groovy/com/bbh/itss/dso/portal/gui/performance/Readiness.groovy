package com.bbh.itss.dso.portal.gui.performance

import groovy.transform.Immutable

@Immutable
class Readiness {

    String selector
    int count
    boolean visible

    static Readiness rendered(String selector, int count) {
        new Readiness(selector, count, false)
    }

    static Readiness shown(String selector) {
        new Readiness(selector, 1, true)
    }

    Map<String, Object> toMap() {
        [selector: selector, count: count, visible: visible]
    }

    String describe() {
        visible ? "`${selector}` visible" : "${count} × `${selector}`"
    }
}
