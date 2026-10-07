package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

final class ProductRules {

    private final Long productId;
    private final Set<Long> ownServiceIds;
    private final ProductDirectory directory;

    ProductRules(Long productId, Set<Long> ownServiceIds, ProductDirectory directory) {
        this.productId = productId;
        this.ownServiceIds = Set.copyOf(ownServiceIds);
        this.directory = requireNonNull(directory, "the product rules need the product directory");
    }

    void check(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> services) {
        requireIdentity(details, appScanAccount, services);
        directory.findProductByCode(details.code())
                .filter(other -> !isThisProduct(other.id()))
                .ifPresent(other -> {
                    throw new IllegalStateException("Product code " + details.code() + " is already used by " + other.name());
                });
        directory.findProductByName(details.name())
                .filter(other -> !isThisProduct(other.id()))
                .ifPresent(other -> {
                    throw new IllegalStateException("A product named " + other.name() + " already exists");
                });

        ValidationProblems problems = new ValidationProblems();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < services.size(); i++) {
            ServiceDraft service = services.get(i);
            ValidationProblems at = problems.at("services[" + i + "]");
            service.settings().validate(at);
            if (service.id() != null && !ownServiceIds.contains(service.id())) {
                at.add("id", "service " + service.id() + " does not belong to this product");
            }
            if (!names.add(service.name().trim().toLowerCase(ROOT))) {
                at.add("name", "another service of this product already uses this name");
            }
        }
        problems.throwIfAny();
    }

    private void requireIdentity(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> services) {
        ValidationProblems problems = new ValidationProblems();
        if (isBlank(details.code())) {
            problems.add("code", "must not be blank");
        }
        if (isBlank(details.name())) {
            problems.add("name", "must not be blank");
        }
        if (details.departmentId() == null) {
            problems.add("departmentId", "choose the product's department");
        } else if (!directory.departmentExists(details.departmentId())) {
            problems.add("departmentId", "department " + details.departmentId() + " does not exist");
        }
        if (appScanAccount == null || appScanAccount.keyId() == null) {
            problems.add("appScan.keyId", "must not be blank");
        }
        for (int i = 0; i < services.size(); i++) {
            if (isBlank(services.get(i).name())) {
                problems.add("services[" + i + "].name", "must not be blank");
            }
        }
        problems.throwIfAny();
    }

    private boolean isThisProduct(long otherId) {
        return productId != null && productId == otherId;
    }
}
