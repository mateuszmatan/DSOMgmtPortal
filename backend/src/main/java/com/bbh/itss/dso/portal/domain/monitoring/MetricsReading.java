package com.bbh.itss.dso.portal.domain.monitoring;

import java.util.function.Supplier;

public record MetricsReading<T>(T value, String error) {

    public static <T> MetricsReading<T> of(Supplier<T> query, T fallback) {
        try {
            return new MetricsReading<>(query.get(), null);
        } catch (MetricsUnavailableException e) {
            return unavailable(fallback, e.getMessage());
        }
    }

    public static <T> MetricsReading<T> unavailable(T fallback, String error) {
        return new MetricsReading<>(fallback, error);
    }

    public boolean failed() {
        return error != null;
    }
}
