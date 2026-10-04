package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.AuditedEntity;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One deployable service of a product, the equivalent of one entry under {@code projects:} in the configuration
 * the DevSecOps library reads. Its settings are value objects, each of which knows how to write and validate
 * itself; single values live in the service's table, lists such as test jobs and deployment targets in tables
 * of their own.
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
    private UnitTestSettings unitTests;

    @Embedded
    private TestSettings tests;

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_TEST_JOB", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @OrderColumn(name = "POSITION")
    private List<TestJob> testJobs = new ArrayList<>();

    @Embedded
    private DeploymentSettings deployment;

    @Embedded
    @AttributeOverride(name = "tasks", column = @Column(name = "DELIVERY_TASKS", length = 1000))
    @AttributeOverride(name = "flags", column = @Column(name = "DELIVERY_FLAGS", length = 2000))
    @AttributeOverride(name = "directory", column = @Column(name = "DELIVERY_DIRECTORY", length = 500))
    @AttributeOverride(name = "mavenHome", column = @Column(name = "DELIVERY_MAVEN_HOME", length = 500))
    @AttributeOverride(name = "environment", column = @Column(name = "DELIVERY_ENVIRONMENT", length = 4000))
    private ToolCommand delivery;

    @Embedded
    private UrbanCodeSettings urbanCode;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<UrbanCodeApplication> urbanCodeApplications = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_SSH_TARGET", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "REGION", length = 10)
    private Map<Region, SshTarget> sshTargets = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_OPENSHIFT_TARGET", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "REGION", length = 10)
    private Map<Region, OpenShiftTarget> openShiftTargets = new HashMap<>();

    @Embedded
    private AppScanSettings appScan;

    @Embedded
    private SonarSettings sonar;

    @Embedded
    private NexusIqSettings nexusIq;

    @Embedded
    private ScmSettings scm;

    @Embedded
    private GoldenFixPolicy goldenFix;

    @Embedded
    private MetricsSettings metrics;

    @Embedded
    private FlutterSettings flutter;

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
        this.unitTests = settings.unitTests();
        this.tests = settings.tests();
        replace(testJobs, settings.testJobs());
        this.deployment = settings.deployment();
        this.delivery = settings.delivery();
        this.urbanCode = settings.urbanCode();
        replaceUrbanCodeApplications(settings.urbanCodeApplications());
        replace(sshTargets, settings.sshTargets());
        replace(openShiftTargets, settings.openShiftTargets());
        this.appScan = settings.appScan();
        this.sonar = settings.sonar();
        this.nexusIq = settings.nexusIq();
        this.scm = settings.scm();
        this.goldenFix = settings.goldenFix();
        this.metrics = settings.metrics().withDefaultProject(product.getCode(), this.name);
        this.flutter = settings.flutter();
    }

    /** The settings with empty sections restored, since JPA loads an embeddable whose columns are all null as null. */
    public ServiceSettings settings() {
        return new ServiceSettings(build, unitTests, tests, List.copyOf(testJobs), deployment, delivery, urbanCode,
                urbanCodeApplications.stream().map(UrbanCodeApplication::settings).toList(), Map.copyOf(sshTargets),
                Map.copyOf(openShiftTargets), appScan, sonar, nexusIq, scm, goldenFix, metrics, flutter);
    }

    /** The project entry of this service, without the BBH-wide defaults. */
    public void writeTo(ConfigTree config) {
        settings().writeTo(config);
        product.getAppScanAccount().writeTo(config);
    }

    void detach() {
        product = null;
    }

    /** Lists are rewritten only when they changed, so saving an unchanged service writes no list rows. */
    private static <T> void replace(List<T> current, List<T> replacement) {
        if (!current.equals(replacement)) {
            current.clear();
            current.addAll(replacement);
        }
    }

    private static <K, V> void replace(Map<K, V> current, Map<K, V> replacement) {
        if (!current.equals(replacement)) {
            current.clear();
            current.putAll(replacement);
        }
    }

    private void replaceUrbanCodeApplications(List<UrbanCodeApplicationSettings> replacement) {
        if (urbanCodeApplications.stream().map(UrbanCodeApplication::settings).toList().equals(replacement)) {
            return;
        }
        urbanCodeApplications.clear();
        for (int position = 0; position < replacement.size(); position++) {
            urbanCodeApplications.add(new UrbanCodeApplication(this, position, replacement.get(position)));
        }
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

    /** Read without loading the service's lists, since monitoring needs it for every pipeline. */
    public MetricsSettings getMetrics() {
        return metrics == null ? MetricsSettings.DEFAULTS : metrics;
    }

    public SonarSettings getSonar() {
        return sonar == null ? SonarSettings.NONE : sonar;
    }

    public AppScanSettings getAppScan() {
        return appScan;
    }

    public NexusIqSettings getNexusIq() {
        return nexusIq == null ? NexusIqSettings.NONE : nexusIq;
    }

    public ScmSettings getScm() {
        return scm == null ? ScmSettings.NONE : scm;
    }

    public BuildSettings getBuild() {
        return build;
    }

    public DeploymentSettings getDeployment() {
        return deployment;
    }
}
