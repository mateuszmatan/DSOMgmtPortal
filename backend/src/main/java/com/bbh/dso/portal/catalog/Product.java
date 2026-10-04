package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * An application onboarded to DevSecOps, for example CertScanner. It groups the services that are built,
 * scanned and deployed by DevSecOps pipelines.
 */
@Entity
@Table(name = "DSO_PRODUCT")
public class Product extends AuditedEntity {

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

    /** HCL AppScan on Cloud API key ID shared by the product's services ({@code asoc.keyId}). */
    @Column(name = "ASOC_KEY_ID", nullable = false, length = 200)
    private String asocKeyId;

    /** Jenkins Secret text credential holding the AppScan key secret ({@code asoc.token}). */
    @Column(name = "ASOC_SECRET_CREDENTIALS_ID", length = 200)
    private String asocSecretCredentialsId;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, name ASC")
    private List<ServiceDefinition> services = new ArrayList<>();

    public void addService(ServiceDefinition service) {
        service.setProduct(this);
        services.add(service);
    }

    public void removeService(ServiceDefinition service) {
        services.remove(service);
        service.setProduct(null);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public void setOwnerTeam(String ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getAsocKeyId() {
        return asocKeyId;
    }

    public void setAsocKeyId(String asocKeyId) {
        this.asocKeyId = asocKeyId;
    }

    public String getAsocSecretCredentialsId() {
        return asocSecretCredentialsId;
    }

    public void setAsocSecretCredentialsId(String asocSecretCredentialsId) {
        this.asocSecretCredentialsId = asocSecretCredentialsId;
    }

    public List<ServiceDefinition> getServices() {
        return services;
    }
}
