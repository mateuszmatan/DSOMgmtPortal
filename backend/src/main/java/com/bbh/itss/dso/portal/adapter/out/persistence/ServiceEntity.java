package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
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
import java.util.Objects;

@Entity
@Table(name = "DSO_SERVICE")
public class ServiceEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private ProductEntity product;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private int displayOrder;

    @Embedded
    private BuildSettingsEmbeddable build;

    @Embedded
    private UnitTestSettingsEmbeddable unitTests;

    @Embedded
    private TestSettingsEmbeddable tests;

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_TEST_JOB", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @OrderColumn(name = "POSITION")
    private List<TestJobEmbeddable> testJobs = new ArrayList<>();

    @Embedded
    private DeploymentSettingsEmbeddable deployment;

    @Embedded
    @AttributeOverride(name = "tasks", column = @Column(name = "DELIVERY_TASKS", length = 1000))
    @AttributeOverride(name = "flags", column = @Column(name = "DELIVERY_FLAGS", length = 2000))
    @AttributeOverride(name = "directory", column = @Column(name = "DELIVERY_DIRECTORY", length = 500))
    @AttributeOverride(name = "mavenHome", column = @Column(name = "DELIVERY_MAVEN_HOME", length = 500))
    @AttributeOverride(name = "environment", column = @Column(name = "DELIVERY_ENVIRONMENT", length = 4000))
    private ToolCommandEmbeddable delivery;

    @Embedded
    private UrbanCodeSettingsEmbeddable urbanCode;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<UrbanCodeApplicationEntity> urbanCodeApplications = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_SSH_TARGET", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "REGION", length = 10)
    private Map<Region, SshTargetEmbeddable> sshTargets = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_OPENSHIFT_TARGET", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "REGION", length = 10)
    private Map<Region, OpenShiftTargetEmbeddable> openShiftTargets = new HashMap<>();

    @Embedded
    private AppScanSettingsEmbeddable appScan;

    @Embedded
    private SonarSettingsEmbeddable sonar;

    @Embedded
    private NexusIqSettingsEmbeddable nexusIq;

    @Embedded
    private ScmSettingsEmbeddable scm;

    @Embedded
    private GoldenFixPolicyEmbeddable goldenFix;

    @Embedded
    private MetricsSettingsEmbeddable metrics;

    @Embedded
    private FlutterSettingsEmbeddable flutter;

    protected ServiceEntity() {
    }

    ServiceEntity(ProductEntity product) {
        this.product = product;
    }

    Long getId() {
        return id;
    }

    ProductEntity product() {
        return product;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    int displayOrder() {
        return displayOrder;
    }

    boolean holdsOtherUniqueValuesThan(String otherName, String sonarProjectKey, String influxProject,
                                       String influxEnv) {
        return !Objects.equals(name, otherName) || !Objects.equals(sonar.projectKey(), sonarProjectKey)
                || !Objects.equals(metrics.influxProject(), influxProject)
                || !Objects.equals(metrics.influxEnv(), influxEnv);
    }

    void releaseUniqueValues() {
        String placeholder = "~" + getId();
        name = placeholder;
        sonar = sonar.withProjectKey(null);
        metrics = metrics.withInfluxProject(placeholder);
    }

    void identity(String name, String description, int displayOrder) {
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    BuildSettingsEmbeddable build() {
        return build;
    }

    void build(BuildSettingsEmbeddable build) {
        this.build = build;
    }

    UnitTestSettingsEmbeddable unitTests() {
        return unitTests;
    }

    void unitTests(UnitTestSettingsEmbeddable unitTests) {
        this.unitTests = unitTests;
    }

    TestSettingsEmbeddable tests() {
        return tests;
    }

    void tests(TestSettingsEmbeddable tests) {
        this.tests = tests;
    }

    List<TestJobEmbeddable> testJobs() {
        return List.copyOf(testJobs);
    }

    void testJobs(List<TestJobEmbeddable> replacement) {
        if (!testJobs.equals(replacement)) {
            testJobs.clear();
            testJobs.addAll(replacement);
        }
    }

    DeploymentSettingsEmbeddable deployment() {
        return deployment;
    }

    void deployment(DeploymentSettingsEmbeddable deployment) {
        this.deployment = deployment;
    }

    ToolCommandEmbeddable delivery() {
        return delivery;
    }

    void delivery(ToolCommandEmbeddable delivery) {
        this.delivery = delivery;
    }

    UrbanCodeSettingsEmbeddable urbanCode() {
        return urbanCode;
    }

    void urbanCode(UrbanCodeSettingsEmbeddable urbanCode) {
        this.urbanCode = urbanCode;
    }

    List<UrbanCodeApplicationSettings> urbanCodeApplications() {
        return urbanCodeApplications.stream().map(UrbanCodeApplicationEntity::toDomain).toList();
    }

    void urbanCodeApplications(List<UrbanCodeApplicationSettings> replacement) {
        if (urbanCodeApplications().equals(replacement)) {
            return;
        }
        urbanCodeApplications.clear();
        for (int position = 0; position < replacement.size(); position++) {
            urbanCodeApplications.add(new UrbanCodeApplicationEntity(this, position, replacement.get(position)));
        }
    }

    Map<Region, SshTargetEmbeddable> sshTargets() {
        return Map.copyOf(sshTargets);
    }

    void sshTargets(Map<Region, SshTargetEmbeddable> replacement) {
        if (!sshTargets.equals(replacement)) {
            sshTargets.clear();
            sshTargets.putAll(replacement);
        }
    }

    Map<Region, OpenShiftTargetEmbeddable> openShiftTargets() {
        return Map.copyOf(openShiftTargets);
    }

    void openShiftTargets(Map<Region, OpenShiftTargetEmbeddable> replacement) {
        if (!openShiftTargets.equals(replacement)) {
            openShiftTargets.clear();
            openShiftTargets.putAll(replacement);
        }
    }

    AppScanSettingsEmbeddable appScan() {
        return appScan;
    }

    void appScan(AppScanSettingsEmbeddable appScan) {
        this.appScan = appScan;
    }

    SonarSettingsEmbeddable sonar() {
        return sonar;
    }

    void sonar(SonarSettingsEmbeddable sonar) {
        this.sonar = sonar;
    }

    NexusIqSettingsEmbeddable nexusIq() {
        return nexusIq;
    }

    void nexusIq(NexusIqSettingsEmbeddable nexusIq) {
        this.nexusIq = nexusIq;
    }

    ScmSettingsEmbeddable scm() {
        return scm;
    }

    void scm(ScmSettingsEmbeddable scm) {
        this.scm = scm;
    }

    GoldenFixPolicyEmbeddable goldenFix() {
        return goldenFix;
    }

    void goldenFix(GoldenFixPolicyEmbeddable goldenFix) {
        this.goldenFix = goldenFix;
    }

    MetricsSettingsEmbeddable metrics() {
        return metrics;
    }

    void metrics(MetricsSettingsEmbeddable metrics) {
        this.metrics = metrics;
    }

    FlutterSettingsEmbeddable flutter() {
        return flutter;
    }

    void flutter(FlutterSettingsEmbeddable flutter) {
        this.flutter = flutter;
    }
}
