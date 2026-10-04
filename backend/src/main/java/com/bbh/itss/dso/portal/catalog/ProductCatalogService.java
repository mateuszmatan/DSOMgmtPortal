package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.ConflictException;
import com.bbh.itss.dso.portal.common.NotFoundException;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductCatalogService {

    private final ProductRepository products;
    private final ServiceDefinitionRepository services;
    private final PipelineStatistics pipelineStatistics;
    private final ApplicationEventPublisher events;

    public ProductCatalogService(ProductRepository products, ServiceDefinitionRepository services,
                                 PipelineStatistics pipelineStatistics, ApplicationEventPublisher events) {
        this.products = products;
        this.services = services;
        this.pipelineStatistics = pipelineStatistics;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> list(String search) {
        Map<Long, Long> serviceCounts = toCountMap(services.countByProduct());
        Map<Long, Long> pipelineCounts = pipelineStatistics.pipelinesPerProduct();
        Map<Long, Long> activeCounts = pipelineStatistics.activePipelinesPerProduct();
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return products.findAllByOrderByNameAsc().stream()
                .filter(p -> needle.isEmpty() || matches(p, needle))
                .map(p -> new ProductSummary(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getOwnerTeam(),
                        serviceCounts.getOrDefault(p.getId(), 0L), pipelineCounts.getOrDefault(p.getId(), 0L),
                        activeCounts.getOrDefault(p.getId(), 0L), p.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    public ProductResponse create(ProductRequest request) {
        validate(request, null);
        Product product = new Product(request.details(), request.appScan());
        for (int order = 0; order < request.services().size(); order++) {
            ServiceRequest service = request.services().get(order);
            product.addService(service.name(), service.description(), order, service.settings());
        }
        return saved(product);
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        if (request.version() != null && request.version() != product.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Product.class, id);
        }
        validate(request, product);
        product.update(request.details(), request.appScan());

        Set<Long> kept = request.services().stream().map(ServiceRequest::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        product.getServices().stream().filter(s -> !kept.contains(s.getId())).toList()
                .forEach(product::removeService);
        products.flush();

        for (int order = 0; order < request.services().size(); order++) {
            ServiceRequest service = request.services().get(order);
            if (service.id() == null) {
                product.addService(service.name(), service.description(), order, service.settings());
            } else {
                product.service(service.id()).orElseThrow()
                        .update(service.name(), service.description(), order, service.settings());
            }
        }
        return saved(product);
    }

    public void delete(Long id) {
        products.delete(find(id));
    }

    private ProductResponse saved(Product product) {
        Product saved = products.saveAndFlush(product);
        events.publishEvent(new ProductChanged(saved.getId()));
        return ProductResponse.from(saved);
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }

    private void validate(ProductRequest request, Product existing) {
        products.findByCodeIgnoreCase(request.code().trim())
                .filter(other -> existing == null || !other.getId().equals(existing.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("Product code " + request.code() + " is already used by " + other.getName());
                });
        products.findByNameIgnoreCase(request.name().trim())
                .filter(other -> existing == null || !other.getId().equals(existing.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A product named " + other.getName() + " already exists");
                });

        Set<Long> ownServiceIds = existing == null ? Set.of()
                : existing.getServices().stream().map(ServiceDefinition::getId).collect(Collectors.toSet());
        ValidationProblems problems = new ValidationProblems();
        UniqueValues names = new UniqueValues();
        UniqueValues metricsTags = new UniqueValues();
        UniqueValues sonarKeys = new UniqueValues();
        for (int i = 0; i < request.services().size(); i++) {
            ServiceRequest service = request.services().get(i);
            ValidationProblems at = problems.at("services[" + i + "]");
            ServiceSettings settings = service.settings();
            settings.validate(at);

            if (service.id() != null && !ownServiceIds.contains(service.id())) {
                at.add("id", "service " + service.id() + " does not belong to this product");
            }
            if (!names.add(service.name())) {
                at.add("name", "another service of this product already uses this name");
            }
            checkMetricsTags(settings.metrics().withDefaultProject(request.code().trim(), service.name().trim()),
                    ownServiceIds, metricsTags, at.at("metrics"));
            checkSonarKey(settings.sonar(), ownServiceIds, sonarKeys, at.at("sonar"));
        }
        problems.throwIfAny();
    }

    private void checkMetricsTags(MetricsSettings metrics, Set<Long> ownServiceIds, UniqueValues seen,
                                  ValidationProblems problems) {
        if (!seen.add(metrics.influxProject() + "|" + metrics.influxEnv())) {
            problems.add("influxProject", "another service of this product writes metrics under the same project and environment");
            return;
        }
        services.findByMetricsTags(metrics.influxProject(), metrics.influxEnv())
                .filter(other -> !ownServiceIds.contains(other.getId()))
                .ifPresent(other -> problems.add("influxProject", "metrics project " + metrics.influxProject() + " ("
                        + metrics.influxEnv() + ") is already used by " + describe(other)));
    }

    private void checkSonarKey(SonarSettings sonar, Set<Long> ownServiceIds, UniqueValues seen,
                               ValidationProblems problems) {
        if (sonar.projectKey() == null) {
            return;
        }
        if (!seen.add(sonar.projectKey())) {
            problems.add("projectKey", "another service of this product uses this key");
            return;
        }
        services.findBySonarProjectKey(sonar.projectKey())
                .filter(other -> !ownServiceIds.contains(other.getId()))
                .ifPresent(other -> problems.add("projectKey", "SonarQube project key is already used by " + describe(other)));
    }

    private static String describe(ServiceDefinition service) {
        return service.getProduct().getName() + " / " + service.getName();
    }

    private static boolean matches(Product product, String needle) {
        return contains(product.getName(), needle) || contains(product.getCode(), needle)
                || contains(product.getOwnerTeam(), needle) || contains(product.getDescription(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static final class UniqueValues {
        private final Set<String> values = new HashSet<>();

        boolean add(String value) {
            return values.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }
}
