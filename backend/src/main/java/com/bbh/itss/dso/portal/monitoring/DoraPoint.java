package com.bbh.itss.dso.portal.monitoring;

import java.time.Instant;

record DoraPoint(Instant time, boolean deployment, boolean changeFailure, long leadTimeSeconds, long durationSeconds) {
}
