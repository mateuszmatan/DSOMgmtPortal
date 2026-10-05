package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Versions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Product {

    private static final Comparator<Service> DISPLAY_ORDER =
            Comparator.comparingInt(Service::displayOrder).thenComparing(Service::name);

    private final Long id;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private ProductDetails details;
    private AppScanAccount appScanAccount;
    private List<Service> services;

    private Product(Long id, ProductDetails details, AppScanAccount appScanAccount, List<Service> services, long version,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.details = details;
        this.appScanAccount = appScanAccount;
        this.services = services.stream().sorted(DISPLAY_ORDER).toList();
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Product create(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> services,
                                 ProductDirectory directory) {
        Product product = new Product(null, details, appScanAccount, List.of(), 0, null, null);
        product.change(details, appScanAccount, services, directory);
        return product;
    }

    public static Product restore(Long id, ProductDetails details, AppScanAccount appScanAccount, List<Service> services,
                                  long version, Instant createdAt, Instant updatedAt) {
        return new Product(id, Objects.requireNonNull(details), appScanAccount, services, version, createdAt, updatedAt);
    }

    public void update(Long expectedVersion, ProductDetails details, AppScanAccount appScanAccount,
                       List<ServiceDraft> services, ProductDirectory directory) {
        Versions.requireCurrent(expectedVersion, version);
        change(details, appScanAccount, services, directory);
    }

    private void change(ProductDetails details, AppScanAccount appScanAccount, List<ServiceDraft> drafts,
                        ProductDirectory directory) {
        Objects.requireNonNull(details, "a product needs its details");
        Objects.requireNonNull(drafts, "a product needs its list of services");
        new ProductRules(id, serviceIds(), directory).check(details, appScanAccount, drafts);
        List<Service> placed = new ArrayList<>();
        for (int order = 0; order < drafts.size(); order++) {
            placed.add(drafts.get(order).place(order, details.code()));
        }
        this.details = details;
        this.appScanAccount = appScanAccount;
        this.services = placed.stream().sorted(DISPLAY_ORDER).toList();
    }

    public Optional<Service> service(Long serviceId) {
        return services.stream().filter(service -> service.hasId(serviceId)).findFirst();
    }

    public Set<Long> serviceIds() {
        Set<Long> ids = new LinkedHashSet<>();
        services.stream().map(Service::id).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    public void writeConfig(Service service, ConfigTree config) {
        service.settings().writeTo(config);
        appScanAccount.writeTo(config);
    }

    public Long id() {
        return id;
    }

    public ProductDetails details() {
        return details;
    }

    public String code() {
        return details.code();
    }

    public String name() {
        return details.name();
    }

    public String description() {
        return details.description();
    }

    public String ownerTeam() {
        return details.ownerTeam();
    }

    public String contactEmail() {
        return details.contactEmail();
    }

    public AppScanAccount appScanAccount() {
        return appScanAccount;
    }

    public List<Service> services() {
        return services;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
