package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductSummary;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository products;
    private final ServiceJpaRepository services;
    private final ProductMapper mapper;

    ProductPersistenceAdapter(ProductJpaRepository products, ServiceJpaRepository services, ProductMapper mapper) {
        this.products = products;
        this.services = services;
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> load(long id) {
        return products.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findByServiceId(long serviceId) {
        return services.findWithProductById(serviceId).map(service -> mapper.toDomain(service.product()));
    }

    @Override
    public List<Product> findAll() {
        return products.findAllByOrderByNameAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ProductSummary> summaries() {
        return products.findAllByOrderByNameAsc().stream()
                .map(product -> new ProductSummary(product.getId(), product.code(), product.name(),
                        product.description(), product.ownerTeam(), product.getUpdatedAt()))
                .toList();
    }

    @Override
    public Map<Long, Long> servicesPerProduct() {
        return Counts.perProduct(services.countByProduct());
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = product.id() == null ? new ProductEntity() : existing(product);
        mapper.copy(product, entity);
        Set<Long> kept = product.serviceIds();
        entity.services().stream().filter(service -> !kept.contains(service.getId())).forEach(entity::removeService);
        if (product.id() != null) {
            products.flush();
        }
        for (Service service : product.services()) {
            ServiceEntity target = service.id() == null ? entity.addService() : entity.service(service.id()).orElseThrow();
            mapper.copy(service, target);
        }
        return mapper.toDomain(products.saveAndFlush(entity));
    }

    @Override
    public void delete(long id) {
        products.findById(id).ifPresent(products::delete);
    }

    @Override
    public Optional<ProductIdentity> findProductByCode(String code) {
        return products.findByCodeIgnoreCase(code).map(ProductPersistenceAdapter::identity);
    }

    @Override
    public Optional<ProductIdentity> findProductByName(String name) {
        return products.findByNameIgnoreCase(name).map(ProductPersistenceAdapter::identity);
    }

    @Override
    public List<ServiceIdentity> findServicesByMetricsTags(String influxProject, String influxEnv) {
        return services.findByMetricsTags(influxProject, influxEnv).stream()
                .map(ProductPersistenceAdapter::identity).toList();
    }

    @Override
    public List<ServiceIdentity> findServicesBySonarProjectKey(String projectKey) {
        return services.findBySonarProjectKey(projectKey).stream().map(ProductPersistenceAdapter::identity).toList();
    }

    private ProductEntity existing(Product product) {
        ProductEntity entity = products.findById(product.id()).orElseThrow(ConflictException::staleVersion);
        if (entity.getVersion() != product.version()) {
            throw ConflictException.staleVersion();
        }
        return entity;
    }

    private static ProductIdentity identity(ProductEntity product) {
        return new ProductIdentity(product.getId(), product.name());
    }

    private static ServiceIdentity identity(ServiceEntity service) {
        return new ServiceIdentity(service.getId(), service.product().name(), service.name());
    }
}
