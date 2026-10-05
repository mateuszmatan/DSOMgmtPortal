package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.monitoring.DoraLevel;
import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DoraSummaryResponse(
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

    static DoraSummaryResponse from(DoraSummary dora) {
        return new DoraSummaryResponse(dora.rangeDays(), dora.runs(), dora.deployments(), dora.deploymentsPerWeek(),
                dora.deploymentFrequencyLevel(), dora.leadTimeMedianSeconds(), dora.leadTimeLevel(),
                dora.changeFailureRatePercent(), dora.changeFailureRateLevel(), dora.meanTimeToRestoreSeconds(),
                dora.timeToRestoreLevel(), dora.restores(), dora.failingSince(), dora.averageDurationSeconds(),
                dora.daily().stream().map(day -> new DailyActivity(day.date(), day.runs(), day.failures(),
                        day.deployments())).toList());
    }

    public record DailyActivity(LocalDate date, int runs, int failures, int deployments) {
    }
}
