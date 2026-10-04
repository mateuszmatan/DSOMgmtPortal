package com.bbh.dso.portal.pipeline;

import com.bbh.dso.portal.catalog.PipelineStatistics;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class PipelineRepositoryStatistics implements PipelineStatistics {

    private final PipelineRepository pipelines;

    PipelineRepositoryStatistics(PipelineRepository pipelines) {
        this.pipelines = pipelines;
    }

    @Override
    public Map<Long, Long> pipelinesPerProduct() {
        return toMap(pipelines.countByProduct());
    }

    @Override
    public Map<Long, Long> activePipelinesPerProduct() {
        return toMap(pipelines.countWithActiveKeyByProduct());
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return counts;
    }
}
