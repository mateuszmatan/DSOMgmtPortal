package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.common.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.util.HashMap;
import java.util.Map;

/**
 * The one row of global settings every pipeline shares. The portal creates it with the DSOEnhanced defaults at
 * its first start; from then on it is changed in the portal only.
 */
@Entity
@Table(name = "DSO_GLOBAL_SETTINGS")
public class GlobalSettings extends AuditedEntity {

    public static final long ID = 1L;

    @Id
    @Column(name = "ID")
    private Long id = ID;

    @Embedded
    private PlatformSettings platform;

    @Embedded
    private DeploymentDefaults deployment;

    @ElementCollection
    @CollectionTable(name = "DSO_GLOBAL_SEVERITY_LIMIT", joinColumns = @JoinColumn(name = "SETTINGS_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "SCANNER", length = 20)
    private Map<Scanner, SeverityLimits> limits = new HashMap<>();

    @Embedded
    private ScanSettings scans;

    @Embedded
    private ReleaseGateSettings releaseGate;

    @Embedded
    private ServiceDefaults serviceDefaults;

    @Embedded
    private GoldenFixPolicy goldenFix;

    protected GlobalSettings() {
    }

    public GlobalSettings(GlobalSettingsValues values) {
        apply(values);
    }

    public final void apply(GlobalSettingsValues values) {
        this.platform = values.platform();
        this.deployment = values.deployment();
        if (!limits.equals(values.limits())) {
            limits.clear();
            limits.putAll(values.limits());
        }
        this.scans = values.scans();
        this.releaseGate = values.releaseGate();
        this.serviceDefaults = values.serviceDefaults();
        this.goldenFix = values.goldenFix();
    }

    public GlobalSettingsValues values() {
        return new GlobalSettingsValues(platform, deployment, limits, scans, releaseGate, serviceDefaults, goldenFix);
    }

    public PlatformSettings platform() {
        return platform;
    }

    public DeploymentDefaults deployment() {
        return deployment;
    }
}
