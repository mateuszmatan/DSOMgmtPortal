package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record ChangeWindow(Instant start, Instant end) {

    static final Duration LONGEST = Duration.ofDays(7);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    public void check(Instant now, ValidationProblems problems) {
        problems.require("start", start, "choose when the change starts");
        problems.require("end", end, "choose when the change ends");
        if (start == null || end == null) {
            return;
        }
        if (!start.isAfter(now)) {
            problems.add("start", "must be in the future");
        }
        if (!end.isAfter(start)) {
            problems.add("end", "must be after the start");
        } else if (Duration.between(start, end).compareTo(LONGEST) > 0) {
            problems.add("end", "a change window may last at most " + LONGEST.toDays() + " days");
        }
    }

    public String text() {
        boolean sameDay = DAY_AND_TIME.format(start).regionMatches(0, DAY_AND_TIME.format(end), 0, 10);
        return DAY_AND_TIME.format(start) + " to " + (sameDay ? TIME : DAY_AND_TIME).format(end) + " UTC";
    }
}
