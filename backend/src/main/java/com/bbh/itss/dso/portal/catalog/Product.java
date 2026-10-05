package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.adapter.out.persistence.AuditedEntity;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "DSO_PRODUCT")
public class Product extends AuditedEntity {

    private static final Comparator<ServiceDefinition> DISPLAY_ORDER =
            Comparator.comparingInt(ServiceDefinition::getDisplayOrder).thenComparing(ServiceDefinition::getName);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "DESCRIPTION", length = 4000)
    private String description;

    @Column(name = "OWNER_TEAM", length = 200)
    private String ownerTeam;

    @Column(name = "CONTACT_EMAIL", length = 320)
    private String contactEmail;

    @Embedded
    private AppScanAccount appScanAccount;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, name ASC")
    private List<ServiceDefinition> services = new ArrayList<>();

    protected Product() {
    }

    public Product(ProductDetails details, AppScanAccount appScanAccount) {
        update(details, appScanAccount);
    }

    public final void update(ProductDetails details, AppScanAccount appScanAccount) {
        this.code = details.code().trim();
        this.name = details.name().trim();
        this.description = Text.trimToNull(details.description());
        this.ownerTeam = Text.trimToNull(details.ownerTeam());
        this.contactEmail = Text.trimToNull(details.contactEmail());
        this.appScanAccount = appScanAccount;
    }

    public ServiceDefinition addService(String name, String description, int displayOrder, ServiceSettings settings) {
        ServiceDefinition service = new ServiceDefinition(this, name, description, displayOrder, settings);
        services.add(service);
        return service;
    }

    public void removeService(ServiceDefinition service) {
        if (services.remove(service)) {
            service.detach();
        }
    }

    public Optional<ServiceDefinition> service(Long serviceId) {
        return services.stream().filter(s -> s.getId() != null && s.getId().equals(serviceId)).findFirst();
    }

    public ProductDetails details() {
        return new ProductDetails(code, name, description, ownerTeam, contactEmail);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public AppScanAccount getAppScanAccount() {
        return appScanAccount;
    }

    public List<ServiceDefinition> getServices() {
        return services.stream().sorted(DISPLAY_ORDER).toList();
    }
}
