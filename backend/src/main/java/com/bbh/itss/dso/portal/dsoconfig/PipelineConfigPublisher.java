package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.catalog.ProductChanged;
import com.bbh.itss.dso.portal.common.Timestamps;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineChanged;
import com.bbh.itss.dso.portal.pipeline.PipelineRepository;
import com.bbh.itss.dso.portal.settings.GlobalSettingsChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

/**
 * Keeps every pipeline's published configuration in step with what it is rendered from. It listens inside the
 * transaction of each change, so the published configuration commits together with the change; at start-up it
 * renders every pipeline again, so a new portal version publishes in its own shape.
 */
@Component
@Transactional
public class PipelineConfigPublisher {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfigPublisher.class);

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
        pipelines.findWithServiceById(event.pipelineId()).ifPresent(pipeline -> publish(pipeline, Timestamps.now()));
    }

    @EventListener
    public void onProductChanged(ProductChanged event) {
        publishAll(pipelines.findByProductId(event.productId()));
    }

    @EventListener
    public void onGlobalSettingsChanged(GlobalSettingsChanged event) {
        publishAll(pipelines.findAllWithService());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<Pipeline> all = pipelines.findAllWithService();
        publishAll(all);
        log.info("Published the configuration of {} pipelines", all.size());
    }

    private void publishAll(List<Pipeline> pipelinesToPublish) {
        Instant now = Timestamps.now();
        pipelinesToPublish.forEach(pipeline -> publish(pipeline, now));
    }

    private void publish(Pipeline pipeline, Instant now) {
        PublishedPipelineConfig config = published.findById(pipeline.getId())
                .orElseGet(() -> new PublishedPipelineConfig(pipeline.getId()));
        config.publish(json.writeValueAsString(builder.pipelineConfig(pipeline)), now);
        published.save(config);
    }
}
