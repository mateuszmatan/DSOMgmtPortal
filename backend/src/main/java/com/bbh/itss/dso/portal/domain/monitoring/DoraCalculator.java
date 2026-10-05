package com.bbh.itss.dso.portal.domain.monitoring;

import com.bbh.itss.dso.portal.domain.monitoring.DoraSummary.DailyActivity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DoraCalculator {

    private static final long HOUR = 3_600;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;

    private DoraCalculator() {
    }

    public static DoraSummary summarize(List<DoraPoint> unsorted, int rangeDays, Instant now) {
        List<DoraPoint> points = unsorted.stream().sorted(Comparator.comparing(DoraPoint::time)).toList();
        int runs = points.size();
        int deployments = (int) points.stream().filter(DoraPoint::deployment).count();
        Double perWeek = rangeDays > 0 ? deployments * 7.0 / rangeDays : null;

        List<DoraPoint> deployed = points.stream().filter(DoraPoint::deployment).toList();
        List<Long> leadTimes = deployed.stream().map(DoraPoint::leadTimeSeconds).filter(s -> s > 0).sorted().toList();
        Long leadMedian = median(leadTimes);

        Double cfr = deployments == 0 ? null
                : 100.0 * deployed.stream().filter(DoraPoint::changeFailure).count() / deployments;

        List<Long> restoreTimes = new ArrayList<>();
        Instant failingSince = null;
        for (DoraPoint point : deployed) {
            if (point.changeFailure()) {
                if (failingSince == null) {
                    failingSince = point.time();
                }
            } else if (failingSince != null) {
                restoreTimes.add(point.time().getEpochSecond() - failingSince.getEpochSecond());
                failingSince = null;
            }
        }
        Long mttr = restoreTimes.isEmpty() ? null
                : Math.round(restoreTimes.stream().mapToLong(Long::longValue).average().orElse(0));

        Long averageDuration = runs == 0 ? null
                : Math.round(points.stream().mapToLong(DoraPoint::durationSeconds).average().orElse(0));

        return new DoraSummary(rangeDays, runs, deployments, perWeek,
                runs == 0 || perWeek == null ? null : deploymentFrequencyLevel(perWeek),
                leadMedian, leadMedian == null ? null : leadTimeLevel(leadMedian),
                cfr, cfr == null ? null : changeFailureRateLevel(cfr),
                mttr, mttr == null ? null : timeToRestoreLevel(mttr),
                restoreTimes.size(), failingSince, averageDuration, daily(points, rangeDays, now));
    }

    static DoraLevel deploymentFrequencyLevel(double perWeek) {
        if (perWeek >= 7) {
            return DoraLevel.ELITE;
        }
        if (perWeek >= 1) {
            return DoraLevel.HIGH;
        }
        if (perWeek >= 7.0 / 30) {
            return DoraLevel.MEDIUM;
        }
        return DoraLevel.LOW;
    }

    static DoraLevel leadTimeLevel(long seconds) {
        return duration(seconds, DAY, WEEK, MONTH);
    }

    static DoraLevel timeToRestoreLevel(long seconds) {
        return duration(seconds, HOUR, DAY, WEEK);
    }

    static DoraLevel changeFailureRateLevel(double percent) {
        if (percent <= 5) {
            return DoraLevel.ELITE;
        }
        if (percent <= 10) {
            return DoraLevel.HIGH;
        }
        if (percent <= 15) {
            return DoraLevel.MEDIUM;
        }
        return DoraLevel.LOW;
    }

    private static DoraLevel duration(long seconds, long elite, long high, long medium) {
        if (seconds < elite) {
            return DoraLevel.ELITE;
        }
        if (seconds < high) {
            return DoraLevel.HIGH;
        }
        if (seconds < medium) {
            return DoraLevel.MEDIUM;
        }
        return DoraLevel.LOW;
    }

    private static Long median(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static List<DailyActivity> daily(List<DoraPoint> points, int rangeDays, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Map<LocalDate, int[]> days = new LinkedHashMap<>();
        for (int i = rangeDays - 1; i >= 0; i--) {
            days.put(today.minusDays(i), new int[3]);
        }
        for (DoraPoint point : points) {
            int[] counts = days.get(LocalDate.ofInstant(point.time(), ZoneOffset.UTC));
            if (counts != null) {
                counts[0]++;
                if (point.changeFailure()) {
                    counts[1]++;
                }
                if (point.deployment()) {
                    counts[2]++;
                }
            }
        }
        return days.entrySet().stream()
                .map(e -> new DailyActivity(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }
}
