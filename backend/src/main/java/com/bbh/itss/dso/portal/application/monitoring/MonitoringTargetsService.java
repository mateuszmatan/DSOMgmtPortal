package com.bbh.itss.dso.portal.application.monitoring;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitoringTargets;
import com.bbh.itss.dso.portal.application.monitoring.port.in.ReadMonitoringTargetsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@UseCase
public class MonitoringTargetsService implements ReadMonitoringTargetsUseCase {

    private final ProductRepositoryPort products;
    private final PipelineRepositoryPort pipelines;
    private final ManageGlobalSettingsUseCase settings;

    public MonitoringTargetsService(ProductRepositoryPort products, PipelineRepositoryPort pipelines,
                                    ManageGlobalSettingsUseCase settings) {
        this.products = products;
        this.pipelines = pipelines;
        this.settings = settings;
    }

    @Override
    @ReadOnly
    public MonitoringTargets everything() {
        List<Product> all = products.findAll();
        Map<Long, Product> byId = all.stream().collect(Collectors.toMap(Product::id, Function.identity()));
        PlatformSettings platform = platform();
        List<PipelineView> views = pipelines.findAll().stream()
                .filter(pipeline -> byId.containsKey(pipeline.service().productId()))
                .map(pipeline -> view(byId.get(pipeline.service().productId()), pipeline, platform))
                .toList();
        return new MonitoringTargets(all, views, platform, pipelines.sharedMetricsTags());
    }

    @Override
    @ReadOnly
    public MonitoringTargets ofProduct(long productId) {
        Product product = products.load(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        PlatformSettings platform = platform();
        return new MonitoringTargets(List.of(product), pipelines.findByProductId(productId).stream()
                .map(pipeline -> view(product, pipeline, platform))
                .toList(), platform, pipelines.sharedMetricsTags());
    }

    @Override
    @ReadOnly
    public MonitoringTargets ofPipeline(long pipelineId) {
        Pipeline pipeline = pipelines.load(pipelineId).orElseThrow(() -> NotFoundException.of("Pipeline", pipelineId));
        long productId = pipeline.service().productId();
        Product product = products.load(productId).orElseThrow(() -> NotFoundException.of("Product", productId));
        PlatformSettings platform = platform();
        return new MonitoringTargets(List.of(product), List.of(view(product, pipeline, platform)), platform,
                pipelines.sharedMetricsTags());
    }

    private PlatformSettings platform() {
        return settings.current().platform();
    }

    private static PipelineView view(Product product, Pipeline pipeline, PlatformSettings platform) {
        return PipelineView.of(product, pipeline, platform.jenkinsUrl());
    }
}
