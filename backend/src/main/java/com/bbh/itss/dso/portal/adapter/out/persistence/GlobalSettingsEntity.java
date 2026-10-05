package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.Scanner;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "DSO_GLOBAL_SETTINGS")
public class GlobalSettingsEntity extends AuditedEntity implements Persistable<Long> {

    static final long ID = 1L;

    @Id
    @Column(name = "ID")
    private Long id = ID;

    @Embedded
    private PlatformSettingsEmbeddable platform;

    @Embedded
    private DeploymentDefaultsEmbeddable deployment;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "DSO_GLOBAL_SEVERITY_LIMIT", joinColumns = @JoinColumn(name = "SETTINGS_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "SCANNER", length = 20)
    private Map<Scanner, SeverityLimitsEmbeddable> limits = new HashMap<>();

    @Embedded
    private ScanSettingsEmbeddable scans;

    @Embedded
    private ReleaseGateSettingsEmbeddable releaseGate;

    @Embedded
    private ServiceDefaultsEmbeddable serviceDefaults;

    @Embedded
    private GoldenFixPolicyEmbeddable goldenFix;

    protected GlobalSettingsEntity() {
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    PlatformSettingsEmbeddable platform() {
        return platform;
    }

    void platform(PlatformSettingsEmbeddable platform) {
        this.platform = platform;
    }

    DeploymentDefaultsEmbeddable deployment() {
        return deployment;
    }

    void deployment(DeploymentDefaultsEmbeddable deployment) {
        this.deployment = deployment;
    }

    Map<Scanner, SeverityLimitsEmbeddable> limits() {
        return Map.copyOf(limits);
    }

    void limits(Map<Scanner, SeverityLimitsEmbeddable> replacement) {
        if (!limits.equals(replacement)) {
            limits.clear();
            limits.putAll(replacement);
        }
    }

    ScanSettingsEmbeddable scans() {
        return scans;
    }

    void scans(ScanSettingsEmbeddable scans) {
        this.scans = scans;
    }

    ReleaseGateSettingsEmbeddable releaseGate() {
        return releaseGate;
    }

    void releaseGate(ReleaseGateSettingsEmbeddable releaseGate) {
        this.releaseGate = releaseGate;
    }

    ServiceDefaultsEmbeddable serviceDefaults() {
        return serviceDefaults;
    }

    void serviceDefaults(ServiceDefaultsEmbeddable serviceDefaults) {
        this.serviceDefaults = serviceDefaults;
    }

    GoldenFixPolicyEmbeddable goldenFix() {
        return goldenFix;
    }

    void goldenFix(GoldenFixPolicyEmbeddable goldenFix) {
        this.goldenFix = goldenFix;
    }
}
