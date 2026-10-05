package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
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

    String name() {
        return name;
    }

    Product toDomain() {
        return Product.restore(id, RecordMapper.map(ProductDetails.class, this),
                RecordMapper.map(appScanAccount, AppScanAccount.class),
                services.stream().map(ServiceEntity::toDomain).toList(), getVersion(), getCreatedAt(), getUpdatedAt());
    }

    void apply(Product product) {
        ProductDetails details = product.details();
        code = details.code();
        name = details.name();
        description = details.description();
        ownerTeam = details.ownerTeam();
        contactEmail = details.contactEmail();
        appScanAccount = RecordMapper.map(product.appScanAccount(), AppScanAccountEmbeddable.class);
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

    @Embeddable
    public record AppScanAccountEmbeddable(
            @Column(name = "ASOC_KEY_ID", nullable = false, length = 200) String keyId,
            @Column(name = "ASOC_SECRET_CREDENTIALS_ID", length = 200) String secretCredentialsId) {
    }
}
