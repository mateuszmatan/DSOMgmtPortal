package com.bbh.itss.dso.portal.monitoring;

import java.time.Instant;

/**
 * One row of the {@code dora} measurement: what a single run contributed to the four DORA metrics.
 */
record DoraPoint(Instant time, boolean deployment, boolean changeFailure, long leadTimeSeconds, long durationSeconds) {
}
