package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;

import java.time.LocalDate;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.apache.commons.lang3.ObjectUtils.anyNull;

public record DateRange(LocalDate from, LocalDate to) {

    static final int MAX_DAYS = 366;

    public DateRange {
        if (anyNull(from, to)) {
            throw InvalidRequestException.of(from == null ? "from" : "to", "choose a date");
        }
        if (to.isBefore(from)) {
            throw InvalidRequestException.of("to", "must not be before the start date " + from);
        }
        if (DAYS.between(from, to) > MAX_DAYS) {
            throw InvalidRequestException.of("to", "the range may span at most " + MAX_DAYS + " days");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
