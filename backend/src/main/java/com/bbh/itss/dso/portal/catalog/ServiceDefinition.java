package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.AuditedEntity;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One deployable service of a product, the equivalent of one entry under {@code projects:} in the
 * config.yaml read by the DevSecOps library. Its settings are value objects, one per config.yaml section,
 * each of which knows how to write and validate itself.
 */
@Entity
@Table(name = "DSO_SERVICE")
public class ServiceDefinition extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private int displayOrder;

    @Embedded
    private BuildSettings build;

    @Embedded
    private DeploymentSettings deployment;

    @Embedded
    private AppScanSettings appScan;

    @Embedded
    private SonarSettings sonar;

    @Embedded
    private NexusIqSettings nexusIq;

    @Embedded
    private ScmSettings scm;

    @Embedded
    private MetricsSettings metrics;

    @Embedded
    private AdditionalConfig additionalConfig;

    protected ServiceDefinition() {
    }

    ServiceDefinition(Product product, String name, String description, int displayOrder, ServiceSettings settings) {
        this.product = product;
        update(name, description, displayOrder, settings);
    }

    /** Replaces the service's details; a missing metrics project tag defaults to {@code <PRODUCT CODE>-<name>}. */
    public void update(String name, String description, int displayOrder, ServiceSettings settings) {
        this.name = name.trim();
        this.description = Text.trimToNull(description);
        this.displayOrder = displayOrder;
        this.build = settings.build();
        this.deployment = settings.deployment();
        this.appScan = settings.appScan();
        this.sonar = settings.sonar();
        this.nexusIq = settings.nexusIq();
        this.scm = settings.scm();
        this.metrics = settings.metrics().withDefaultProject(product.getCode(), this.name);
        this.additionalConfig = settings.additionalConfig();
    }

    /** The settings with empty sections restored, since JPA loads an embeddable whose columns are all null as null. */
    public ServiceSettings settings() {
        return new ServiceSettings(build, deployment, appScan, sonar, nexusIq, scm, metrics, additionalConfig);
    }

    /** The project entry of this service in config.yaml, without the BBH-wide defaults. */
    public void writeTo(ConfigTree config) {
        settings().sections().forEach(section -> section.writeTo(config));
        product.getAppScanAccount().writeTo(config);
    }

    void detach() {
        product = null;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public MetricsSettings getMetrics() {
        return settings().metrics();
    }

    public SonarSettings getSonar() {
        return settings().sonar();
    }

    public BuildSettings getBuild() {
        return build;
    }

    public DeploymentSettings getDeployment() {
        return deployment;
    }
}
