package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory.ServiceIdentity;
import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ProductRules {

    private final Long productId;
    private final Set<Long> ownServiceIds;
    private final ProductDirectory directory;

    ProductRules(Long productId, Set<Long> ownServiceIds, ProductDirectory directory) {
        this.productId = productId;
        this.ownServiceIds = Set.copyOf(ownServiceIds);
        this.directory = Objects.requireNonNull(directory, "the product rules need the product directory");
    }

    void check(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> services) {
        requireIdentity(details, appScanAccount, services);
        directory.findProductByCode(details.code())
                .filter(other -> !isThisProduct(other.id()))
                .ifPresent(other -> {
                    throw new ConflictException("Product code " + details.code() + " is already used by " + other.name());
                });
        directory.findProductByName(details.name())
                .filter(other -> !isThisProduct(other.id()))
                .ifPresent(other -> {
                    throw new ConflictException("A product named " + other.name() + " already exists");
                });

        ValidationProblems problems = new ValidationProblems();
        UniqueValues names = new UniqueValues();
        UniqueValues metricsTags = new UniqueValues();
        UniqueValues sonarKeys = new UniqueValues();
        for (int i = 0; i < services.size(); i++) {
            ServiceDraft service = services.get(i);
            ValidationProblems at = problems.at("services[" + i + "]");
            ServiceSettings settings = service.settings();
            settings.validate(at);
            if (service.id() != null && !ownServiceIds.contains(service.id())) {
                at.add("id", "service " + service.id() + " does not belong to this product");
            }
            if (!names.add(service.name())) {
                at.add("name", "another service of this product already uses this name");
            }
            checkMetricsTags(settings.metrics().withDefaultProject(details.code(), service.name()), metricsTags,
                    at.at("metrics"));
            checkSonarKey(settings.sonar(), sonarKeys, at.at("sonar"));
        }
        problems.throwIfAny();
    }

    private void requireIdentity(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> services) {
        ValidationProblems problems = new ValidationProblems();
        if (Text.isBlank(details.code())) {
            problems.add("code", "must not be blank");
        }
        if (Text.isBlank(details.name())) {
            problems.add("name", "must not be blank");
        }
        if (appScanAccount == null || appScanAccount.keyId() == null) {
            problems.add("appScan.keyId", "must not be blank");
        }
        for (int i = 0; i < services.size(); i++) {
            if (Text.isBlank(services.get(i).name())) {
                problems.add("services[" + i + "].name", "must not be blank");
            }
        }
        problems.throwIfAny();
    }

    private void checkMetricsTags(MetricsSettings metrics, UniqueValues seen, ValidationProblems problems) {
        if (!seen.add(metrics.influxProject() + "|" + metrics.influxEnv())) {
            problems.add("influxProject", "another service of this product writes metrics under the same project and environment");
            return;
        }
        foreign(directory.findServicesByMetricsTags(metrics.influxProject(), metrics.influxEnv()))
                .ifPresent(other -> problems.add("influxProject", "metrics project " + metrics.influxProject() + " ("
                        + metrics.influxEnv() + ") is already used by " + other.describe()));
    }

    private void checkSonarKey(SonarSettings sonar, UniqueValues seen, ValidationProblems problems) {
        if (sonar.projectKey() == null) {
            return;
        }
        if (!seen.add(sonar.projectKey())) {
            problems.add("projectKey", "another service of this product uses this key");
            return;
        }
        foreign(directory.findServicesBySonarProjectKey(sonar.projectKey()))
                .ifPresent(other -> problems.add("projectKey", "SonarQube project key is already used by " + other.describe()));
    }

    private Optional<ServiceIdentity> foreign(List<ServiceIdentity> services) {
        return services.stream().filter(other -> !ownServiceIds.contains(other.id())).findFirst();
    }

    private boolean isThisProduct(long otherId) {
        return productId != null && productId == otherId;
    }

    private static final class UniqueValues {

        private final Set<String> values = new HashSet<>();

        boolean add(String value) {
            return values.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }
}
