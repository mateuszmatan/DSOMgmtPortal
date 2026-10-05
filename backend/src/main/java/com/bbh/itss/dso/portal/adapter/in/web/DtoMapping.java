package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.Region;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class DtoMapping {

    private DtoMapping() {
    }

    static <S, T> T mapped(S source, Function<S, T> mapping) {
        return source == null ? null : mapping.apply(source);
    }

    static <S, T> List<T> list(List<S> source, Function<S, T> mapping) {
        return source == null ? null : source.stream().map(mapping).toList();
    }

    static <S, T> Map<Region, T> regions(Map<Region, S> source, Function<S, T> mapping) {
        if (source == null) {
            return null;
        }
        Map<Region, T> mapped = new EnumMap<>(Region.class);
        source.forEach((region, value) -> {
            if (value != null) {
                mapped.put(region, mapping.apply(value));
            }
        });
        return mapped;
    }
}
