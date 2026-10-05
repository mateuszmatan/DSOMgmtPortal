package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ManageProductsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.shared.NotFoundException;

import java.util.List;
import java.util.Map;

@UseCase
public class ProductCatalogService implements ManageProductsUseCase, QueryProductsUseCase {

    private final ProductRepositoryPort products;
    private final PipelineCountsPort pipelineCounts;
    private final PublishPipelineConfigsUseCase publisher;

    public ProductCatalogService(ProductRepositoryPort products, PipelineCountsPort pipelineCounts,
                                 PublishPipelineConfigsUseCase publisher) {
        this.products = products;
        this.pipelineCounts = pipelineCounts;
        this.publisher = publisher;
    }

    @Override
    @ReadOnly
    public List<ProductSummaryView> list(String search) {
        Map<Long, Long> serviceCounts = products.servicesPerProduct();
        Map<Long, Long> pipelines = pipelineCounts.pipelinesPerProduct();
        Map<Long, Long> active = pipelineCounts.activePipelinesPerProduct();
        return products.summaries().stream()
                .filter(product -> product.matches(search))
                .map(product -> new ProductSummaryView(product.id(), product.code(), product.name(),
                        product.description(), product.ownerTeam(), serviceCounts.getOrDefault(product.id(), 0L),
                        pipelines.getOrDefault(product.id(), 0L), active.getOrDefault(product.id(), 0L),
                        product.updatedAt()))
                .toList();
    }

    @Override
    @ReadOnly
    public Product get(long id) {
        return find(id);
    }

    @Override
    public Product create(ProductCommand command) {
        publisher.lockConfigurations();
        return saved(Product.create(command.details(), command.appScan(), command.drafts(), products));
    }

    @Override
    public Product update(long id, ProductCommand command) {
        publisher.lockConfigurations();
        Product product = find(id);
        product.update(command.version(), command.details(), command.appScan(), command.drafts(), products);
        return saved(product);
    }

    @Override
    public void delete(long id) {
        publisher.lockConfigurations();
        find(id);
        products.delete(id);
    }

    private Product saved(Product product) {
        Product saved = products.save(product);
        publisher.productChanged(saved.id());
        return saved;
    }

    private Product find(long id) {
        return products.load(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }
}
