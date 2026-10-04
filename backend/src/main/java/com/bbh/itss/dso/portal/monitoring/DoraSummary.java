package com.bbh.itss.dso.portal.monitoring;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The four DORA metrics of one pipeline over a time range, with the daily activity behind them.
 *
 * @param deploymentsPerWeek          deployments divided by the weeks in the range
 * @param leadTimeMedianSeconds       median time from the commit under test to the end of the run
 * @param changeFailureRatePercent    share of runs that did not end SUCCESS
 * @param meanTimeToRestoreSeconds    mean time from the first failing run to the next green one
 * @param failingSince                start of the current failure streak, when the last run failed
 */
public record DoraSummary(
        int rangeDays,
        int runs,
        int deployments,
        Double deploymentsPerWeek,
        DoraLevel deploymentFrequencyLevel,
        Long leadTimeMedianSeconds,
        DoraLevel leadTimeLevel,
        Double changeFailureRatePercent,
        DoraLevel changeFailureRateLevel,
        Long meanTimeToRestoreSeconds,
        DoraLevel timeToRestoreLevel,
        int restores,
        Instant failingSince,
        Long averageDurationSeconds,
        List<DailyActivity> daily) {

    public record DailyActivity(LocalDate date, int runs, int failures, int deployments) {
    }
}
