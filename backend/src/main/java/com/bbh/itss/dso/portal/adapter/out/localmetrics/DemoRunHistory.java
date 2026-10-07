package com.bbh.itss.dso.portal.adapter.out.localmetrics;

import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

@Component
@ConditionalOnExpression(LocalMetricsStore.ACTIVE)
class DemoRunHistory {

    static final Duration HISTORY = Duration.ofDays(120);
    private static final Duration RETIRED = Duration.ofDays(9);
    private static final long SEED = 20_261_007L;
    private static final Logger log = LoggerFactory.getLogger(DemoRunHistory.class);

    private final LocalMetricsStore store;
    private final ReadMonitoringTargetsUseCase targets;
    private final Clock clock;

    DemoRunHistory(LocalMetricsStore store, ReadMonitoringTargetsUseCase targets, Clock clock) {
        this.store = store;
        this.targets = targets;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recordOnce() {
        if (store.count() > 0) {
            return;
        }
        Map<String, List<PipelineView>> byJob = new TreeMap<>();
        for (PipelineView view : targets.everything().pipelines()) {
            byJob.computeIfAbsent(view.metricsTag() + " " + view.pipeline().type() + " "
                    + view.pipeline().settings().jobPath(), key -> new ArrayList<>()).add(view);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<StoredPoint> points = new ArrayList<>();
        byJob.forEach((key, views) -> {
            PipelineView first = views.getFirst();
            boolean retired = views.stream().noneMatch(view -> view.pipeline().isEnabled());
            points.addAll(new RunHistory(first.metricsTag(), first.pipeline().settings().jobPath(),
                    first.pipeline().type(), views.stream().map(view -> view.service().name()).distinct().toList(),
                    new Random(SEED + key.hashCode())).points(now.minus(HISTORY), retired ? now.minus(RETIRED) : now));
        });
        store.save(points);
        log.info("Recorded a random run history of {} pipelines in the local metrics store", byJob.size());
    }
}
