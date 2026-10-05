package com.bbh.itss.dso.portal.adapter.out.persistence;

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
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "DSO_PRODUCT")
public class ProductEntity extends AuditedEntity {

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
    private AppScanAccountEmbeddable appScanAccount;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, name ASC")
    private List<ServiceEntity> services = new ArrayList<>();

    protected ProductEntity() {
    }

    Long getId() {
        return id;
    }

    String code() {
        return code;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    String ownerTeam() {
        return ownerTeam;
    }

    String contactEmail() {
        return contactEmail;
    }

    AppScanAccountEmbeddable appScanAccount() {
        return appScanAccount;
    }

    void details(String code, String name, String description, String ownerTeam, String contactEmail,
                 AppScanAccountEmbeddable appScanAccount) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.ownerTeam = ownerTeam;
        this.contactEmail = contactEmail;
        this.appScanAccount = appScanAccount;
    }

    List<ServiceEntity> services() {
        return List.copyOf(services);
    }

    Optional<ServiceEntity> service(Long serviceId) {
        return services.stream().filter(service -> serviceId.equals(service.getId())).findFirst();
    }

    ServiceEntity addService() {
        ServiceEntity service = new ServiceEntity(this);
        services.add(service);
        return service;
    }

    void removeService(ServiceEntity service) {
        services.remove(service);
    }
}
