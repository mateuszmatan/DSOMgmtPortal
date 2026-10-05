package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import com.bbh.itss.dso.portal.catalog.ProductChanged;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineChanged;
import com.bbh.itss.dso.portal.pipeline.PipelineRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

@Component
@Transactional
public class PipelineConfigPublisher implements PublishPipelineConfigsUseCase {

    private final PipelineRepository pipelines;
    private final PublishedPipelineConfigRepository published;
    private final DsoConfigBuilder builder;
    private final JsonMapper json;

    public PipelineConfigPublisher(PipelineRepository pipelines, PublishedPipelineConfigRepository published,
                                   DsoConfigBuilder builder, JsonMapper json) {
        this.pipelines = pipelines;
        this.published = published;
        this.builder = builder;
        this.json = json;
    }

    @EventListener
    public void onPipelineChanged(PipelineChanged event) {
        pipelineChanged(event.pipelineId());
    }

    @EventListener
    public void onProductChanged(ProductChanged event) {
        productChanged(event.productId());
    }

    @Override
    public void pipelineChanged(long pipelineId) {
        pipelines.findWithServiceById(pipelineId).ifPresent(pipeline -> publish(pipeline, Timestamps.now()));
    }

    @Override
    public void productChanged(long productId) {
        publishEach(pipelines.findByProductId(productId));
    }

    @Override
    public void settingsChanged() {
        publishAll();
    }

    @Override
    public int publishAll() {
        List<Pipeline> all = pipelines.findAllWithService();
        publishEach(all);
        return all.size();
    }

    private void publishEach(List<Pipeline> pipelinesToPublish) {
        Instant now = Timestamps.now();
        pipelinesToPublish.forEach(pipeline -> publish(pipeline, now));
    }

    private void publish(Pipeline pipeline, Instant now) {
        PublishedPipelineConfig config = published.findById(pipeline.getId())
                .orElseGet(() -> new PublishedPipelineConfig(pipeline.getId()));
        if (config.publish(json.writeValueAsString(builder.pipelineConfig(pipeline)), now) && config.isNew()) {
            published.save(config);
        }
    }
}
