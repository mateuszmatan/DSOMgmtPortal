package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.type.NumericBooleanConverter;

/**
 * One deployable service of a product, the equivalent of one entry under {@code projects:} in the
 * config.yaml read by the DevSecOps library. Settings with a dedicated column are the ones the library
 * requires or that identify the service in AppScan, SonarQube, Nexus IQ and InfluxDB; everything else
 * lives in {@link #additionalConfig}.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "BUILD_TOOL", nullable = false, length = 20)
    private BuildTool buildTool;

    @Enumerated(EnumType.STRING)
    @Column(name = "DEPLOY_TARGET", nullable = false, length = 20)
    private DeployTarget deployTarget;

    /** {@code sourceDir}: source root for the IRX generation. */
    @Column(name = "SOURCE_DIR", nullable = false, length = 500)
    private String sourceDir;

    /** {@code javaPath}: JDK exported as JAVA_HOME for the build and the tests. */
    @Column(name = "JAVA_PATH", length = 500)
    private String javaPath;

    /** {@code buildToolAutoSetup}: detect the Java version instead of {@link #javaPath}. */
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "BUILD_TOOL_AUTO_SETUP", nullable = false)
    private boolean buildToolAutoSetup;

    /** {@code appId}: HCL AppScan application ID used by SAST and DAST. */
    @Column(name = "APPSCAN_APP_ID", nullable = false, length = 36)
    private String appScanAppId;

    /** {@code sast.scanName} */
    @Column(name = "SAST_SCAN_NAME", length = 200)
    private String sastScanName;

    /** {@code dast.enabled} */
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "DAST_ENABLED", nullable = false)
    private boolean dastEnabled;

    /** {@code dast.targetUrl} */
    @Column(name = "DAST_TARGET_URL", length = 1000)
    private String dastTargetUrl;

    /** {@code dast.presenceId} */
    @Column(name = "DAST_PRESENCE_ID", length = 100)
    private String dastPresenceId;

    /** {@code tools.sonar.projectName} */
    @Column(name = "SONAR_PROJECT_NAME", length = 200)
    private String sonarProjectName;

    /** {@code tools.sonar.projectKey} */
    @Column(name = "SONAR_PROJECT_KEY", length = 400)
    private String sonarProjectKey;

    /** {@code tools.nexusIq.application} */
    @Column(name = "NEXUS_IQ_APPLICATION", length = 200)
    private String nexusIqApplication;

    /** {@code tools.nexusIq.scanPatterns}, one Ant pattern per line. */
    @Column(name = "NEXUS_IQ_SCAN_PATTERNS", length = 2000)
    private String nexusIqScanPatterns;

    /** {@code scm.bitbucket.url}: repository GoldenFix raises pull requests against. */
    @Column(name = "REPOSITORY_URL", length = 1000)
    private String repositoryUrl;

    /** {@code scm.bitbucket.credentialsId} */
    @Column(name = "BITBUCKET_CREDENTIALS_ID", length = 200)
    private String bitbucketCredentialsId;

    /** {@code goldenFix.enabled} */
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "GOLDEN_FIX_ENABLED", nullable = false)
    private boolean goldenFixEnabled;

    /** {@code influx.enabled} */
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "METRICS_ENABLED", nullable = false)
    private boolean metricsEnabled;

    /** {@code influx.project}: base of the InfluxDB {@code project} tag. */
    @Column(name = "INFLUX_PROJECT", nullable = false, length = 200)
    private String influxProject;

    /** {@code influx.env}: InfluxDB {@code env} tag. */
    @Column(name = "INFLUX_ENV", nullable = false, length = 50)
    private String influxEnv;

    /** {@code appName}: OpenShift application name. */
    @Column(name = "APP_NAME", length = 200)
    private String appName;

    /** {@code artifactName}: OpenShift artifact copied into the build context. */
    @Column(name = "ARTIFACT_NAME", length = 300)
    private String artifactName;

    /** YAML merged into the generated config.yaml section: build, tests, deploy and every other key without a column. */
    @Lob
    @Column(name = "ADDITIONAL_CONFIG")
    private String additionalConfig;

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    void setProduct(Product product) {
        this.product = product;
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

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public BuildTool getBuildTool() {
        return buildTool;
    }

    public void setBuildTool(BuildTool buildTool) {
        this.buildTool = buildTool;
    }

    public DeployTarget getDeployTarget() {
        return deployTarget;
    }

    public void setDeployTarget(DeployTarget deployTarget) {
        this.deployTarget = deployTarget;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public boolean isBuildToolAutoSetup() {
        return buildToolAutoSetup;
    }

    public void setBuildToolAutoSetup(boolean buildToolAutoSetup) {
        this.buildToolAutoSetup = buildToolAutoSetup;
    }

    public String getAppScanAppId() {
        return appScanAppId;
    }

    public void setAppScanAppId(String appScanAppId) {
        this.appScanAppId = appScanAppId;
    }

    public String getSastScanName() {
        return sastScanName;
    }

    public void setSastScanName(String sastScanName) {
        this.sastScanName = sastScanName;
    }

    public boolean isDastEnabled() {
        return dastEnabled;
    }

    public void setDastEnabled(boolean dastEnabled) {
        this.dastEnabled = dastEnabled;
    }

    public String getDastTargetUrl() {
        return dastTargetUrl;
    }

    public void setDastTargetUrl(String dastTargetUrl) {
        this.dastTargetUrl = dastTargetUrl;
    }

    public String getDastPresenceId() {
        return dastPresenceId;
    }

    public void setDastPresenceId(String dastPresenceId) {
        this.dastPresenceId = dastPresenceId;
    }

    public String getSonarProjectName() {
        return sonarProjectName;
    }

    public void setSonarProjectName(String sonarProjectName) {
        this.sonarProjectName = sonarProjectName;
    }

    public String getSonarProjectKey() {
        return sonarProjectKey;
    }

    public void setSonarProjectKey(String sonarProjectKey) {
        this.sonarProjectKey = sonarProjectKey;
    }

    public String getNexusIqApplication() {
        return nexusIqApplication;
    }

    public void setNexusIqApplication(String nexusIqApplication) {
        this.nexusIqApplication = nexusIqApplication;
    }

    public String getNexusIqScanPatterns() {
        return nexusIqScanPatterns;
    }

    public void setNexusIqScanPatterns(String nexusIqScanPatterns) {
        this.nexusIqScanPatterns = nexusIqScanPatterns;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBitbucketCredentialsId() {
        return bitbucketCredentialsId;
    }

    public void setBitbucketCredentialsId(String bitbucketCredentialsId) {
        this.bitbucketCredentialsId = bitbucketCredentialsId;
    }

    public boolean isGoldenFixEnabled() {
        return goldenFixEnabled;
    }

    public void setGoldenFixEnabled(boolean goldenFixEnabled) {
        this.goldenFixEnabled = goldenFixEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public String getInfluxProject() {
        return influxProject;
    }

    public void setInfluxProject(String influxProject) {
        this.influxProject = influxProject;
    }

    public String getInfluxEnv() {
        return influxEnv;
    }

    public void setInfluxEnv(String influxEnv) {
        this.influxEnv = influxEnv;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }

    public String getAdditionalConfig() {
        return additionalConfig;
    }

    public void setAdditionalConfig(String additionalConfig) {
        this.additionalConfig = additionalConfig;
    }
}
