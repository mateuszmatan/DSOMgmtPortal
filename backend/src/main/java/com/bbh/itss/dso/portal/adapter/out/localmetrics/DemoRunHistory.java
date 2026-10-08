package com.bbh.itss.dso.portal.adapter.out.localmetrics;

import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static com.bbh.itss.dso.portal.adapter.out.localmetrics.LocalMetricsStore.ACTIVE;
import static java.time.Duration.ofDays;
import static java.time.temporal.ChronoUnit.SECONDS;

@Component
@ConditionalOnExpression(ACTIVE)
@RequiredArgsConstructor
@Slf4j
class DemoRunHistory {

    static final Duration HISTORY = ofDays(120);
    private static final Duration RETIRED = ofDays(9);
    private static final long SEED = 20_261_007L;

    private final LocalMetricsStore store;
    private final ReadMonitoringTargetsUseCase targets;
    private final Clock clock;

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
        Instant now = clock.instant().truncatedTo(SECONDS);
        List<StoredPoint> points = new ArrayList<>();
        byJob.forEach((key, views) -> {
            PipelineView first = views.getFirst();
            boolean retired = views.stream().noneMatch(view -> view.pipeline().isEnabled());
            points.addAll(new RunHistory(first.metricsTag(), first.pipeline().settings().jobPath(),
                    first.pipeline().type(), repositories(views), new Random(SEED + key.hashCode()))
                    .points(now.minus(HISTORY), retired ? now.minus(RETIRED) : now));
        });
        store.save(points);
        log.info("Recorded a random run history of {} pipelines in the local metrics store", byJob.size());
    }

    static Map<String, String> repositories(List<PipelineView> views) {
        Map<String, String> repositories = new LinkedHashMap<>();
        views.forEach(view -> repositories.putIfAbsent(view.service().name(),
                view.service().settings().scm().repositoryUrl()));
        return repositories;
    }
}
