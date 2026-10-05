package com.bbh.itss.dso.portal.domain.monitoring

import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.ELITE
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.HIGH
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.LOW
import static com.bbh.itss.dso.portal.domain.monitoring.DoraLevel.MEDIUM

class DoraCalculatorSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-04T12:00:00Z')

    def "no runs give no metrics, only empty days"() {
        when:
        def summary = DoraCalculator.summarize([], 7, NOW)

        then:
        summary.rangeDays() == 7
        summary.runs() == 0
        summary.deployments() == 0
        summary.deploymentsPerWeek() == 0.0d
        summary.deploymentFrequencyLevel() == null
        summary.leadTimeMedianSeconds() == null
        summary.leadTimeLevel() == null
        summary.changeFailureRatePercent() == null
        summary.changeFailureRateLevel() == null
        summary.meanTimeToRestoreSeconds() == null
        summary.timeToRestoreLevel() == null
        summary.restores() == 0
        summary.failingSince() == null
        summary.averageDurationSeconds() == null
        summary.daily()*.date() == (0..6).collect { LocalDate.parse('2026-09-28').plusDays(it) }
        summary.daily().every { it.runs() == 0 && it.failures() == 0 && it.deployments() == 0 }
    }

    def "a month of runs in any order gives the four metrics"() {
        given:
        def points = [
                point('2026-10-03T08:30:00Z', true, false, 5400, 600),
                point('2026-10-01T10:00:00Z', true, false, 3600, 600),
                point('2026-10-02T09:00:00Z', false, true, 7200, 900),
                point('2026-10-04T10:00:00Z', false, true, 3600, 300),
                point('2026-10-02T11:00:00Z', false, true, 0, 300),
                point('2026-10-02T13:00:00Z', true, false, 10800, 600),
                point('2026-10-03T08:00:00Z', false, true, 1800, 600)]

        when:
        def summary = DoraCalculator.summarize(points, 30, NOW)

        then: 'three deployments in 30 days'
        summary.runs() == 7
        summary.deployments() == 3
        summary.deploymentsPerWeek() == 0.7d
        summary.deploymentFrequencyLevel() == MEDIUM

        and: 'the median of the positive lead times'
        summary.leadTimeMedianSeconds() == 4500
        summary.leadTimeLevel() == ELITE

        and: 'four of seven runs failed'
        Math.abs(summary.changeFailureRatePercent() - 57.142857d) < 0.0001
        summary.changeFailureRateLevel() == LOW

        and: 'restored after four hours and after half an hour, failing again since the last run'
        summary.restores() == 2
        summary.meanTimeToRestoreSeconds() == 8100
        summary.timeToRestoreLevel() == HIGH
        summary.failingSince() == Instant.parse('2026-10-04T10:00:00Z')
        summary.averageDurationSeconds() == 557

        and: 'the activity of each day'
        summary.daily().size() == 30
        summary.daily().last().date() == LocalDate.parse('2026-10-04')
        day(summary, '2026-10-02') == [3, 2, 1]
        day(summary, '2026-10-04') == [1, 1, 0]
        day(summary, '2026-09-30') == [0, 0, 0]
    }

    def "runs before the range count in the totals but not per day"() {
        when:
        def summary = DoraCalculator.summarize([point('2026-08-01T10:00:00Z', true, false, 60, 60)], 7, NOW)

        then:
        summary.runs() == 1
        summary.daily().sum { it.runs() } == 0
    }

    def "a range without days has no deployment rate"() {
        when:
        def summary = DoraCalculator.summarize([point('2026-10-04T10:00:00Z', true, false, 60, 60)], 0, NOW)

        then:
        summary.deploymentsPerWeek() == null
        summary.deploymentFrequencyLevel() == null
        summary.daily() == []
    }

    def "an odd number of lead times has the middle one as median"() {
        when:
        def summary = DoraCalculator.summarize([point('2026-10-01T10:00:00Z', true, false, 100, 1),
                                                point('2026-10-02T10:00:00Z', true, false, 900, 1),
                                                point('2026-10-03T10:00:00Z', true, false, 300, 1)], 30, NOW)

        then:
        summary.leadTimeMedianSeconds() == 300
    }

    def "#perWeek deployments a week rate #level"() {
        expect:
        DoraCalculator.deploymentFrequencyLevel(perWeek) == level

        where:
        perWeek  || level
        7.0d     || ELITE
        6.99d    || HIGH
        1.0d     || HIGH
        0.99d    || MEDIUM
        7.0d / 30 || MEDIUM
        0.2d     || LOW
    }

    def "a lead time of #seconds s rates #level"() {
        expect:
        DoraCalculator.leadTimeLevel(seconds) == level

        where:
        seconds   || level
        86_399    || ELITE
        86_400    || HIGH
        604_799   || HIGH
        604_800   || MEDIUM
        2_591_999 || MEDIUM
        2_592_000 || LOW
    }

    def "a time to restore of #seconds s rates #level"() {
        expect:
        DoraCalculator.timeToRestoreLevel(seconds) == level

        where:
        seconds || level
        3_599   || ELITE
        3_600   || HIGH
        86_400  || MEDIUM
        604_800 || LOW
    }

    def "a change failure rate of #percent % rates #level"() {
        expect:
        DoraCalculator.changeFailureRateLevel(percent) == level

        where:
        percent || level
        0.0d    || ELITE
        5.0d    || ELITE
        5.01d   || HIGH
        10.0d   || HIGH
        15.0d   || MEDIUM
        15.1d   || LOW
    }

    private static DoraPoint point(String time, boolean deployment, boolean failure, long lead, long duration) {
        new DoraPoint(Instant.parse(time), deployment, failure, lead, duration)
    }

    private static List<Integer> day(DoraSummary summary, String date) {
        def day = summary.daily().find { it.date() == LocalDate.parse(date) }
        [day.runs(), day.failures(), day.deployments()]
    }
}
