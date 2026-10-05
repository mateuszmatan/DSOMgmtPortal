package com.bbh.itss.dso.portal.domain.monitoring;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DoraSummary(int rangeDays, int runs, int deployments, Double deploymentsPerWeek,
                          DoraLevel deploymentFrequencyLevel, Long leadTimeMedianSeconds, DoraLevel leadTimeLevel,
                          Double changeFailureRatePercent, DoraLevel changeFailureRateLevel,
                          Long meanTimeToRestoreSeconds, DoraLevel timeToRestoreLevel, int restores,
                          Instant failingSince, Long averageDurationSeconds, List<DailyActivity> daily) {

    public record DailyActivity(LocalDate date, int runs, int failures, int deployments) {
    }
}
