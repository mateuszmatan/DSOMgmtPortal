package com.bbh.itss.dso.portal.application.dsoconfig;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.ReadPipelineConfigUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.ReadPublishedConfigUseCase;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.RenderConfigUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.dsoconfig.DsoConfigBuilder;

import java.util.Map;

@UseCase
public class PipelineConfigService implements ReadPipelineConfigUseCase, RenderConfigUseCase {

    private final ManagePipelineKeysUseCase keys;
    private final ReadPublishedConfigUseCase published;
    private final QueryPipelinesUseCase pipelines;
    private final QueryProductsUseCase products;
    private final ManageGlobalSettingsUseCase settings;

    public PipelineConfigService(ManagePipelineKeysUseCase keys, ReadPublishedConfigUseCase published,
                                 QueryPipelinesUseCase pipelines, QueryProductsUseCase products,
                                 ManageGlobalSettingsUseCase settings) {
        this.keys = keys;
        this.published = published;
        this.pipelines = pipelines;
        this.products = products;
        this.settings = settings;
    }

    @Override
    public Map<String, Object> readByKey(String key) {
        long pipelineId = keys.authorizeKey(key);
        return published.currentConfig(pipelineId).orElseGet(() -> config(pipelines.get(pipelineId)));
    }

    @Override
    @ReadOnly
    public Map<String, Object> pipelineConfig(long pipelineId) {
        return config(pipelines.get(pipelineId));
    }

    @Override
    @ReadOnly
    public Map<String, Object> productConfig(long productId) {
        return builder().productConfig(products.get(productId));
    }

    @Override
    @ReadOnly
    public Map<String, Object> settingsConfig() {
        return builder().globalConfig();
    }

    private Map<String, Object> config(PipelineView view) {
        return builder().pipelineConfig(view.product(), view.service(), view.pipeline());
    }

    private DsoConfigBuilder builder() {
        return new DsoConfigBuilder(settings.current().values());
    }
}
