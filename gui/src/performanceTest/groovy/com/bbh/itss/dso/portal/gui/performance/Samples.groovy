package com.bbh.itss.dso.portal.gui.performance

class Samples {

    final String scenario
    final String readyWhen
    final double limitMillis
    private final List<Double> values = []

    Samples(String scenario, String readyWhen, double limitMillis) {
        this.scenario = scenario
        this.readyWhen = readyWhen
        this.limitMillis = limitMillis
    }

    Samples leftShift(double value) {
        values << value
        this
    }

    List<Double> getValues() {
        List.copyOf(values)
    }

    double getMedian() {
        def sorted = values.sort(false)
        def middle = sorted.size().intdiv(2)
        sorted.size() % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
    }

    double getP95() {
        def sorted = values.sort(false)
        sorted[Math.max(0, (int) Math.ceil(0.95d * sorted.size()) - 1)]
    }

    double getMax() {
        values.max()
    }

    boolean isWithinLimit() {
        !values.isEmpty() && p95 <= limitMillis
    }
}
