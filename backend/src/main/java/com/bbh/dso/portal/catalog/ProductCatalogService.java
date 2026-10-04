package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.ConflictException;
import com.bbh.dso.portal.common.InvalidRequestException;
import com.bbh.dso.portal.common.InvalidRequestException.FieldProblem;
import com.bbh.dso.portal.common.NotFoundException;
import com.bbh.dso.portal.dsoconfig.AdditionalConfigValidator;
import com.bbh.dso.portal.pipeline.PipelineRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adds, changes and removes products together with their services, applying the rules of the DevSecOps
 * library that a single field annotation cannot express.
 */
@Service
@Transactional
public class ProductCatalogService {

    static final String DEFAULT_SOURCE_DIR = ".";
    static final String DEFAULT_INFLUX_ENV = "test";

    private final ProductRepository products;
    private final ServiceDefinitionRepository services;
    private final PipelineRepository pipelines;
    private final AdditionalConfigValidator additionalConfigValidator;

    public ProductCatalogService(ProductRepository products, ServiceDefinitionRepository services,
                                 PipelineRepository pipelines, AdditionalConfigValidator additionalConfigValidator) {
        this.products = products;
        this.services = services;
        this.pipelines = pipelines;
        this.additionalConfigValidator = additionalConfigValidator;
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> list(String search) {
        Map<Long, Long> serviceCounts = toCountMap(services.countByProduct());
        Map<Long, Long> pipelineCounts = toCountMap(pipelines.countByProduct());
        Map<Long, Long> activeCounts = toCountMap(pipelines.countWithActiveKeyByProduct());
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
        Product product = new Product();
        applyProduct(product, request);
        int order = 0;
        for (ServiceRequest serviceRequest : request.services()) {
            ServiceDefinition service = new ServiceDefinition();
            applyService(service, serviceRequest, request.code(), order++);
            product.addService(service);
        }
        return ProductResponse.from(products.saveAndFlush(product));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        if (request.version() != null && request.version() != product.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Product.class, id);
        }
        validate(request, product);
        applyProduct(product, request);

        Map<Long, ServiceDefinition> stored = product.getServices().stream()
                .collect(Collectors.toMap(ServiceDefinition::getId, Function.identity()));
        Set<Long> kept = request.services().stream().map(ServiceRequest::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (ServiceDefinition service : new ArrayList<>(product.getServices())) {
            if (!kept.contains(service.getId())) {
                product.removeService(service);
            }
        }
        // Removals are flushed first so a re-added service may reuse a removed one's unique values.
        products.flush();

        int order = 0;
        for (ServiceRequest serviceRequest : request.services()) {
            ServiceDefinition service = serviceRequest.id() == null ? new ServiceDefinition() : stored.get(serviceRequest.id());
            applyService(service, serviceRequest, request.code(), order++);
            if (serviceRequest.id() == null) {
                product.addService(service);
            }
        }
        return ProductResponse.from(products.saveAndFlush(product));
    }

    public void delete(Long id) {
        products.delete(find(id));
    }

    Product find(Long id) {
        return products.findById(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }

    private void validate(ProductRequest request, Product existing) {
        products.findByCodeIgnoreCase(request.code())
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
        List<FieldProblem> problems = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> influxTags = new HashSet<>();
        Set<String> sonarKeys = new HashSet<>();
        for (int i = 0; i < request.services().size(); i++) {
            ServiceRequest service = request.services().get(i);
            String path = "services[" + i + "].";
            if (service.id() != null && !ownServiceIds.contains(service.id())) {
                problems.add(new FieldProblem(path + "id", "service " + service.id() + " does not belong to this product"));
            }
            if (!names.add(service.name().toLowerCase(Locale.ROOT))) {
                problems.add(new FieldProblem(path + "name", "another service of this product already uses this name"));
            }
            if (service.buildTool() != BuildTool.FLUTTER && isBlank(service.javaPath()) && !service.buildToolAutoSetup()) {
                problems.add(new FieldProblem(path + "javaPath",
                        "set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them"));
            }
            if (service.dastEnabled() && isBlank(service.dastTargetUrl())) {
                problems.add(new FieldProblem(path + "dastTargetUrl", "is required when DAST is enabled"));
            }
            if (service.deployTarget() == DeployTarget.OPENSHIFT) {
                if (isBlank(service.appName())) {
                    problems.add(new FieldProblem(path + "appName", "is required for OpenShift deployment"));
                }
                if (isBlank(service.artifactName())) {
                    problems.add(new FieldProblem(path + "artifactName", "is required for OpenShift deployment"));
                }
            }
            for (String problem : additionalConfigValidator.validate(service.additionalConfig())) {
                problems.add(new FieldProblem(path + "additionalConfig", problem));
            }

            String influxProject = influxProject(service, request.code());
            String influxEnv = influxEnv(service);
            String tag = (influxProject + "|" + influxEnv).toLowerCase(Locale.ROOT);
            if (!influxTags.add(tag)) {
                problems.add(new FieldProblem(path + "influxProject",
                        "another service of this product writes metrics under the same project and environment"));
            } else {
                services.findByInfluxProjectIgnoreCaseAndInfluxEnvIgnoreCase(influxProject, influxEnv)
                        .filter(other -> !Objects.equals(other.getId(), service.id()) && !ownServiceIds.contains(other.getId()))
                        .ifPresent(other -> problems.add(new FieldProblem(path + "influxProject",
                                "metrics project " + influxProject + " (" + influxEnv + ") is already used by "
                                        + other.getProduct().getName() + " / " + other.getName())));
            }
            if (!isBlank(service.sonarProjectKey())) {
                String key = service.sonarProjectKey().trim();
                if (!sonarKeys.add(key)) {
                    problems.add(new FieldProblem(path + "sonarProjectKey", "another service of this product uses this key"));
                } else {
                    services.findBySonarProjectKey(key)
                            .filter(other -> !Objects.equals(other.getId(), service.id()) && !ownServiceIds.contains(other.getId()))
                            .ifPresent(other -> problems.add(new FieldProblem(path + "sonarProjectKey",
                                    "SonarQube project key is already used by " + other.getProduct().getName() + " / "
                                            + other.getName())));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException(problems);
        }
    }

    private static void applyProduct(Product product, ProductRequest request) {
        product.setCode(request.code().trim());
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setOwnerTeam(trimToNull(request.ownerTeam()));
        product.setContactEmail(trimToNull(request.contactEmail()));
        product.setAsocKeyId(request.asocKeyId().trim());
        product.setAsocSecretCredentialsId(trimToNull(request.asocSecretCredentialsId()));
    }

    private static void applyService(ServiceDefinition service, ServiceRequest request, String productCode, int order) {
        service.setName(request.name().trim());
        service.setDescription(trimToNull(request.description()));
        service.setDisplayOrder(order);
        service.setBuildTool(request.buildTool());
        service.setDeployTarget(request.deployTarget());
        service.setSourceDir(isBlank(request.sourceDir()) ? DEFAULT_SOURCE_DIR : request.sourceDir().trim());
        service.setJavaPath(trimToNull(request.javaPath()));
        service.setBuildToolAutoSetup(request.buildToolAutoSetup());
        service.setAppScanAppId(request.appScanAppId().trim().toLowerCase(Locale.ROOT));
        service.setSastScanName(trimToNull(request.sastScanName()));
        service.setDastEnabled(request.dastEnabled());
        service.setDastTargetUrl(trimToNull(request.dastTargetUrl()));
        service.setDastPresenceId(trimToNull(request.dastPresenceId()));
        service.setSonarProjectName(trimToNull(request.sonarProjectName()));
        service.setSonarProjectKey(trimToNull(request.sonarProjectKey()));
        service.setNexusIqApplication(trimToNull(request.nexusIqApplication()));
        service.setNexusIqScanPatterns(ScanPatterns.join(request.nexusIqScanPatterns()));
        service.setRepositoryUrl(trimToNull(request.repositoryUrl()));
        service.setBitbucketCredentialsId(trimToNull(request.bitbucketCredentialsId()));
        service.setGoldenFixEnabled(request.goldenFixEnabled());
        service.setMetricsEnabled(request.metricsEnabled());
        service.setInfluxProject(influxProject(request, productCode));
        service.setInfluxEnv(influxEnv(request));
        service.setAppName(trimToNull(request.appName()));
        service.setArtifactName(trimToNull(request.artifactName()));
        service.setAdditionalConfig(isBlank(request.additionalConfig()) ? null : request.additionalConfig().strip());
    }

    /** Defaults to {@code <PRODUCT CODE>-<service name>}, unique across the portal like the product code. */
    static String influxProject(ServiceRequest request, String productCode) {
        return isBlank(request.influxProject()) ? productCode.trim() + "-" + request.name().trim() : request.influxProject().trim();
    }

    static String influxEnv(ServiceRequest request) {
        return isBlank(request.influxEnv()) ? DEFAULT_INFLUX_ENV : request.influxEnv().trim();
    }

    private static boolean matches(Product product, String needle) {
        return contains(product.getName(), needle) || contains(product.getCode(), needle)
                || contains(product.getOwnerTeam(), needle) || contains(product.getDescription(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
