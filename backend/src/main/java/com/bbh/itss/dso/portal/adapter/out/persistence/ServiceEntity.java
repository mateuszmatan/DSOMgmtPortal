package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.type.NumericBooleanConverter;

import java.util.ArrayList;
import java.util.EnumMap;
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
    private SettingsEmbeddable settings;

    @ElementCollection
    @CollectionTable(name = "DSO_SERVICE_TEST_JOB", joinColumns = @JoinColumn(name = "SERVICE_ID"))
    @OrderColumn(name = "POSITION")
    private List<TestJobEmbeddable> testJobs = new ArrayList<>();

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

    List<UrbanCodeApplicationSettings> urbanCodeApplications() {
        return urbanCodeApplications.stream().map(UrbanCodeApplicationEntity::toDomain).toList();
    }

    Service toDomain() {
        return new Service(id, name, description, displayOrder, RecordMapper.map(ServiceSettings.class, settings, this));
    }

    void apply(Service service) {
        ServiceSettings source = service.settings();
        name = service.name();
        description = service.description();
        displayOrder = service.displayOrder();
        settings = RecordMapper.map(source, SettingsEmbeddable.class);
        replace(testJobs, source.testJobs().stream().map(job -> RecordMapper.map(job, TestJobEmbeddable.class)).toList());
        replace(sshTargets, regions(source.sshTargets(), SshTargetEmbeddable.class));
        replace(openShiftTargets, regions(source.openShiftTargets(), OpenShiftTargetEmbeddable.class));
        List<UrbanCodeApplicationSettings> applications = source.urbanCodeApplications();
        if (!applications.equals(urbanCodeApplications())) {
            urbanCodeApplications.clear();
            for (int position = 0; position < applications.size(); position++) {
                urbanCodeApplications.add(new UrbanCodeApplicationEntity(this, position, applications.get(position)));
            }
        }
    }

    boolean holdsOtherUniqueValuesThan(Service service) {
        ServiceSettings wanted = service.settings();
        return !Objects.equals(name, service.name())
                || !Objects.equals(settings.sonar().projectKey(), wanted.sonar().projectKey())
                || !Objects.equals(settings.metrics().influxProject(), wanted.metrics().influxProject())
                || !Objects.equals(settings.metrics().influxEnv(), wanted.metrics().influxEnv());
    }

    void releaseUniqueValues() {
        String placeholder = "~" + id;
        name = placeholder;
        settings = settings.withoutUniqueValues(placeholder);
    }

    private static <T> void replace(List<T> current, List<T> replacement) {
        if (!current.equals(replacement)) {
            current.clear();
            current.addAll(replacement);
        }
    }

    private static <T> void replace(Map<Region, T> current, Map<Region, T> replacement) {
        if (!current.equals(replacement)) {
            current.clear();
            current.putAll(replacement);
        }
    }

    private static <S, T> Map<Region, T> regions(Map<Region, S> targets, Class<T> type) {
        Map<Region, T> mapped = new EnumMap<>(Region.class);
        targets.forEach((region, target) -> mapped.put(region, RecordMapper.map(target, type)));
        return mapped;
    }

    @Embeddable
    public record SettingsEmbeddable(
            @Embedded BuildSettingsEmbeddable build,
            @Embedded UnitTestSettingsEmbeddable unitTests,
            @Embedded TestSettingsEmbeddable tests,
            @Embedded DeploymentSettingsEmbeddable deployment,
            @Embedded
            @AttributeOverride(name = "tasks", column = @Column(name = "DELIVERY_TASKS", length = 1000))
            @AttributeOverride(name = "flags", column = @Column(name = "DELIVERY_FLAGS", length = 2000))
            @AttributeOverride(name = "directory", column = @Column(name = "DELIVERY_DIRECTORY", length = 500))
            @AttributeOverride(name = "mavenHome", column = @Column(name = "DELIVERY_MAVEN_HOME", length = 500))
            @AttributeOverride(name = "environment", column = @Column(name = "DELIVERY_ENVIRONMENT", length = 4000))
            ToolCommandEmbeddable delivery,
            @Embedded UrbanCodeSettingsEmbeddable urbanCode,
            @Embedded AppScanSettingsEmbeddable appScan,
            @Embedded SonarSettingsEmbeddable sonar,
            @Embedded NexusIqSettingsEmbeddable nexusIq,
            @Embedded ScmSettingsEmbeddable scm,
            @Embedded GoldenFixPolicyEmbeddable goldenFix,
            @Embedded MetricsSettingsEmbeddable metrics,
            @Embedded FlutterSettingsEmbeddable flutter) {

        SettingsEmbeddable withoutUniqueValues(String placeholder) {
            return new SettingsEmbeddable(build, unitTests, tests, deployment, delivery, urbanCode, appScan,
                    sonar.withProjectKey(null), nexusIq, scm, goldenFix, metrics.withInfluxProject(placeholder), flutter);
        }
    }

    @Embeddable
    public record BuildSettingsEmbeddable(
            @Enumerated(EnumType.STRING) @Column(name = "BUILD_TOOL", nullable = false, length = 20) BuildTool tool,
            @Column(name = "SOURCE_DIR", nullable = false, length = 500) String sourceDir,
            @Column(name = "JAVA_PATH", length = 500) String javaPath,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "BUILD_TOOL_AUTO_SETUP", nullable = false) Boolean autoSetup,
            @Column(name = "BUILD_PATH", length = 500) String buildPath,
            @Embedded @AttributeOverride(name = "tasks", column = @Column(name = "BUILD_TASKS", length = 1000))
            @AttributeOverride(name = "flags", column = @Column(name = "BUILD_FLAGS", length = 2000))
            @AttributeOverride(name = "directory", column = @Column(name = "BUILD_DIRECTORY", length = 500))
            @AttributeOverride(name = "mavenHome", column = @Column(name = "BUILD_MAVEN_HOME", length = 500))
            @AttributeOverride(name = "environment", column = @Column(name = "BUILD_ENVIRONMENT", length = 4000))
            ToolCommandEmbeddable command) {
    }

    @Embeddable
    public record UnitTestSettingsEmbeddable(
            @Embedded @AttributeOverride(name = "tasks", column = @Column(name = "UNIT_TEST_TASKS", length = 1000))
            @AttributeOverride(name = "flags", column = @Column(name = "UNIT_TEST_FLAGS", length = 2000))
            @AttributeOverride(name = "directory", column = @Column(name = "UNIT_TEST_DIRECTORY", length = 500))
            @AttributeOverride(name = "mavenHome", column = @Column(name = "UNIT_TEST_MAVEN_HOME", length = 500))
            @AttributeOverride(name = "environment", column = @Column(name = "UNIT_TEST_ENVIRONMENT", length = 4000))
            ToolCommandEmbeddable command,
            @Column(name = "UNIT_TEST_RESULTS", length = 500) String resultPattern,
            @Column(name = "UNIT_TEST_ROOT_DIR", length = 500) String rootDir,
            @Column(name = "UNIT_TEST_REPORT_DIR", length = 500) String reportOutDir,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "UNIT_TEST_ALLOW_EMPTY", nullable = false) Boolean allowEmptyResults,
            @Column(name = "COVERAGE_REPORT_PATH", length = 500) String coverageReportPath) {
    }

    @Embeddable
    public record TestSettingsEmbeddable(
            @Column(name = "TESTS_MAX_PARALLEL") Integer maxParallel,
            @Column(name = "SMOKE_MAX_PARALLEL") Integer smokeMaxParallel,
            @Column(name = "REGRESSION_MAX_PARALLEL") Integer regressionMaxParallel,
            @Column(name = "PERFORMANCE_MAX_PARALLEL") Integer performanceMaxParallel) {
    }

    @Embeddable
    public record TestJobEmbeddable(
            @Enumerated(EnumType.STRING) @Column(name = "STAGE", nullable = false, length = 20) TestStage stage,
            @Column(name = "NAME", length = 200) String name,
            @Enumerated(EnumType.STRING) @Column(name = "JOB_TYPE", length = 20) TestJobType type,
            @Column(name = "JOB", nullable = false, length = 1000) String job,
            @Column(name = "TIMEOUT_MINUTES") Integer timeoutMinutes,
            @Column(name = "PARAMETERS", length = 2000) String parameters,
            @Column(name = "REMOTE_JENKINS", length = 200) String remoteJenkins,
            @Column(name = "REMOTE_JENKINS_URL", length = 1000) String remoteJenkinsUrl,
            @Column(name = "CREDENTIALS_ID", length = 200) String credentialsId) {
    }

    @Embeddable
    public record DeploymentSettingsEmbeddable(
            @Enumerated(EnumType.STRING)
            @Column(name = "DEPLOY_TARGET", nullable = false, length = 20) DeployTarget target,
            @Column(name = "APP_NAME", length = 200) String appName,
            @Column(name = "ARTIFACT_NAME", length = 300) String artifactName,
            @Column(name = "BASE_ARTIFACT_NAME", length = 300) String baseArtifactName) {
    }

    @Embeddable
    public record ToolCommandEmbeddable(
            @Convert(converter = DelimitedListConverter.Tokens.class)
            @Column(name = "TASKS", length = 1000) List<String> tasks,
            @Convert(converter = DelimitedListConverter.Tokens.class)
            @Column(name = "FLAGS", length = 2000) List<String> flags,
            @Column(name = "DIRECTORY", length = 500) String directory,
            @Column(name = "MAVEN_HOME", length = 500) String mavenHome,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "ENVIRONMENT", length = 4000) List<String> environment) {
    }

    @Embeddable
    public record UrbanCodeSettingsEmbeddable(
            @Column(name = "UCD_SITE_NAME", length = 200) String siteName,
            @Column(name = "UCD_DEPLOY_PROCESS", length = 200) String deployProcess,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "UCD_SKIP_WAIT", nullable = false) Boolean skipWait,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "UCD_DEPLOY_WITH_SNAPSHOT", nullable = false) Boolean deployWithSnapshot,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "UCD_UPDATE_SNAPSHOT_COMPONENTS", nullable = false) Boolean updateSnapshotComponents,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "UCD_INCLUDE_ONLY_DEPLOY_VERSIONS", nullable = false) Boolean includeOnlyDeployVersions,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "UCD_DEPLOY_ONLY_CHANGED", nullable = false) Boolean deployOnlyChanged,
            @Column(name = "UCD_DEPLOY_DESCRIPTION", length = 1000) String deployDescription,
            @Column(name = "UCD_REQUEST_PROPERTIES", length = 2000) String requestProperties) {
    }

    @Embeddable
    public record UrbanCodeComponentEmbeddable(
            @Column(name = "COMPONENT_NAME", nullable = false, length = 200) String componentName,
            @Column(name = "BASE_DIR", length = 500) String baseDir,
            @Column(name = "FILE_INCLUDE_PATTERNS", length = 500) String fileIncludePatterns,
            @Column(name = "FILE_EXCLUDE_PATTERNS", length = 500) String fileExcludePatterns,
            @Column(name = "VERSION_PREFIX", length = 200) String versionPrefix,
            @Column(name = "COMPONENT_VERSION", length = 200) String version,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "INCREMENTAL_VERSION", nullable = false) Boolean incrementalVersion) {
    }

    @Embeddable
    public record SshTargetEmbeddable(
            @Column(name = "HOST", length = 255) String host,
            @Column(name = "SSH_USER", length = 100) String user,
            @Column(name = "DEPLOY_DIR", length = 500) String deployDir,
            @Column(name = "DEPLOY_SCRIPT", length = 500) String deployScript,
            @Column(name = "VERSION_FILE", length = 500) String versionFile) {
    }

    @Embeddable
    public record OpenShiftTargetEmbeddable(
            @Column(name = "PROJECT_BUILD", length = 200) String projectBuild,
            @Column(name = "BUILD_CONFIG_PATH", length = 500) String buildConfigPath,
            @Column(name = "DOCKER_FILE_PATH", length = 500) String dockerFilePath,
            @Column(name = "BUILD_CONTEXT", length = 500) String buildContext,
            @Column(name = "ADD_FILE", length = 500) String addFile,
            @Column(name = "DOCKER_REPO_PUSH", length = 500) String dockerRepoPush,
            @Column(name = "DOCKER_REPO_PULL", length = 500) String dockerRepoPull,
            @Column(name = "CERT_DIR", length = 500) String certDir,
            @Column(name = "NEXUS_AUTH_FILE", length = 500) String nexusAuthFile,
            @Column(name = "PROJECT_DEPLOYMENT", length = 200) String projectDeployment,
            @Column(name = "DEPLOY_CONFIG_PATH", length = 500) String deployConfigPath,
            @Column(name = "CONFIG_PATH", length = 500) String configPath,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "SKIP_CONFIG_DEPLOY", nullable = false) Boolean skipConfigDeploy,
            @Column(name = "HEALTH_CHECK_URL", length = 500) String healthCheckUrl,
            @Column(name = "ROUTE_HOSTNAME", length = 300) String routeHostname,
            @Column(name = "DEPLOYMENT_PATH", length = 500) String deploymentPath,
            @Column(name = "DEPLOYMENT_REPO_URL", length = 1000) String deploymentRepoUrl,
            @Column(name = "DEPLOYMENT_REPO_BRANCH", length = 200) String deploymentRepoBranch,
            @Column(name = "DEPLOYMENT_REPO_CREDENTIALS_ID", length = 200) String deploymentRepoCredentialsId) {
    }

    @Embeddable
    public record AppScanSettingsEmbeddable(
            @Column(name = "APPSCAN_APP_ID", nullable = false, length = 36) String applicationId,
            @Column(name = "SAST_SCAN_NAME", length = 200) String sastScanName,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "SAST_INCLUDED_DIRS", length = 2000) List<String> includedDirs,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "SAST_EXCLUDED_DIRS", length = 2000) List<String> excludedDirs,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "APPSCAN_COMPILE", nullable = false) Boolean compile,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "APPSCAN_SOURCE_CODE_ONLY", nullable = false) Boolean sourceCodeOnly,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "APPSCAN_USE_CONFIG_FILE", nullable = false) Boolean useConfigFile,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "APPSCAN_INSECURE_TLS", nullable = false) Boolean insecureTls,
            @Column(name = "APPSCAN_CLIENT_PATH", length = 500) String clientPath,
            @Embedded
            @AttributeOverride(name = "tasks", column = @Column(name = "APPSCAN_COMPILE_TASKS", length = 1000))
            @AttributeOverride(name = "flags", column = @Column(name = "APPSCAN_COMPILE_FLAGS", length = 2000))
            @AttributeOverride(name = "directory", column = @Column(name = "APPSCAN_COMPILE_DIRECTORY", length = 500))
            @AttributeOverride(name = "mavenHome", column = @Column(name = "APPSCAN_COMPILE_MAVEN_HOME", length = 500))
            @AttributeOverride(name = "environment", column = @Column(name = "APPSCAN_COMPILE_ENVIRONMENT", length = 4000))
            ToolCommandEmbeddable compileCommand,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "DAST_ENABLED", nullable = false) Boolean dastEnabled,
            @Column(name = "DAST_SCAN_NAME", length = 200) String dastScanName,
            @Column(name = "DAST_TARGET_URL", length = 1000) String dastTargetUrl,
            @Column(name = "DAST_PRESENCE_ID", length = 100) String dastPresenceId) {
    }

    @Embeddable
    public record SonarSettingsEmbeddable(
            @Column(name = "SONAR_PROJECT_NAME", length = 200) String projectName,
            @Column(name = "SONAR_PROJECT_KEY", length = 400) String projectKey,
            @Column(name = "SONAR_INSTALLATION_NAME", length = 200) String installationName,
            @Column(name = "SONAR_CREDENTIALS_ID", length = 200) String credentialsId,
            @Column(name = "SONAR_AUTH_TOKEN_CREDENTIALS_ID", length = 200) String authTokenCredentialsId,
            @Column(name = "SONAR_BADGE_TOKEN", length = 200) String badgeToken,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "SONAR_ADD_BADGES", nullable = false) Boolean addBadges,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "SONAR_FULL_BADGES", nullable = false) Boolean fullBadges,
            @Embedded @AttributeOverride(name = "tasks", column = @Column(name = "SONAR_TASKS", length = 1000))
            @AttributeOverride(name = "flags", column = @Column(name = "SONAR_FLAGS", length = 2000))
            @AttributeOverride(name = "directory", column = @Column(name = "SONAR_DIRECTORY", length = 500))
            @AttributeOverride(name = "mavenHome", column = @Column(name = "SONAR_MAVEN_HOME", length = 500))
            @AttributeOverride(name = "environment", column = @Column(name = "SONAR_ENVIRONMENT", length = 4000))
            ToolCommandEmbeddable command) {

        SonarSettingsEmbeddable withProjectKey(String key) {
            return new SonarSettingsEmbeddable(projectName, key, installationName, credentialsId, authTokenCredentialsId,
                    badgeToken, addBadges, fullBadges, command);
        }
    }

    @Embeddable
    public record NexusIqSettingsEmbeddable(
            @Column(name = "NEXUS_IQ_APPLICATION", length = 200) String application,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "NEXUS_IQ_SCAN_PATTERNS", length = 2000) List<String> scanPatterns,
            @Column(name = "NEXUS_IQ_STAGE", nullable = false, length = 50) String stage,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "NEXUS_IQ_FAIL_ON_NETWORK_ERROR", nullable = false) Boolean failOnNetworkError,
            @Column(name = "SCA_SCAN_NAME", length = 200) String scaScanName) {
    }

    @Embeddable
    public record ScmSettingsEmbeddable(
            @Column(name = "REPOSITORY_URL", length = 1000) String repositoryUrl,
            @Column(name = "BITBUCKET_CREDENTIALS_ID", length = 200) String credentialsId,
            @Enumerated(EnumType.STRING)
            @Column(name = "BITBUCKET_AUTH_TYPE", nullable = false, length = 20) BitbucketAuthType authType,
            @Enumerated(EnumType.STRING) @Column(name = "BITBUCKET_TYPE", length = 20) BitbucketType type,
            @Column(name = "BITBUCKET_TARGET_BRANCH", length = 200) String targetBranch,
            @Column(name = "BITBUCKET_CLONE_URL", length = 1000) String cloneUrl,
            @Convert(converter = DelimitedListConverter.Commas.class)
            @Column(name = "BITBUCKET_REVIEWERS", length = 2000) List<String> reviewers,
            @Column(name = "BITBUCKET_API_URL", length = 1000) String apiUrl,
            @Column(name = "BITBUCKET_WORKSPACE", length = 200) String workspace,
            @Column(name = "BITBUCKET_PROJECT_KEY", length = 200) String projectKey,
            @Column(name = "BITBUCKET_REPO_SLUG", length = 200) String repoSlug) {
    }

    @Embeddable
    public record GoldenFixPolicyEmbeddable(
            @Convert(converter = NumericBooleanConverter.class) @Column(name = "GOLDEN_FIX_ENABLED") Boolean enabled,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "GOLDEN_FIX_DIRECT_ONLY") Boolean onlyDirectDependencies,
            @Column(name = "GOLDEN_FIX_MIN_THREAT_LEVEL") Integer minThreatLevel,
            @Convert(converter = DelimitedListConverter.Commas.class)
            @Column(name = "GOLDEN_FIX_ECOSYSTEMS", length = 200) List<String> ecosystems,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "GOLDEN_FIX_VERSION_TYPES", length = 1000) List<String> goldenVersionTypes,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "GOLDEN_FIX_EXCLUDE_DIRS", length = 2000) List<String> excludeDirs,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "GOLDEN_FIX_VERIFY") Boolean verifyEnabled,
            @Column(name = "GOLDEN_FIX_VERIFY_ATTEMPTS") Integer verifyMaxAttempts,
            @Column(name = "GOLDEN_FIX_VERIFY_TIMEOUT") Integer verifyTimeoutMinutes,
            @Column(name = "GOLDEN_FIX_VERIFY_MAVEN", length = 500) String verifyMavenCommand,
            @Column(name = "GOLDEN_FIX_VERIFY_GRADLE", length = 500) String verifyGradleCommand,
            @Column(name = "GOLDEN_FIX_VERIFY_NPM", length = 500) String verifyNpmCommand,
            @Column(name = "GOLDEN_FIX_VERIFY_PIP", length = 500) String verifyPipCommand,
            @Column(name = "GOLDEN_FIX_VERIFY_PUB", length = 500) String verifyPubCommand,
            @Column(name = "GOLDEN_FIX_AUTHOR_NAME", length = 200) String commitAuthorName,
            @Column(name = "GOLDEN_FIX_AUTHOR_EMAIL", length = 320) String commitAuthorEmail,
            @Column(name = "GOLDEN_FIX_TIME_ZONE", length = 100) String timeZone) {
    }

    @Embeddable
    public record MetricsSettingsEmbeddable(
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "METRICS_ENABLED", nullable = false) Boolean enabled,
            @Column(name = "INFLUX_PROJECT", nullable = false, length = 200) String influxProject,
            @Column(name = "INFLUX_ENV", nullable = false, length = 50) String influxEnv) {

        MetricsSettingsEmbeddable withInfluxProject(String project) {
            return new MetricsSettingsEmbeddable(enabled, project, influxEnv);
        }
    }

    @Embeddable
    public record FlutterSettingsEmbeddable(
            @Enumerated(EnumType.STRING) @Column(name = "FLUTTER_PLATFORM", length = 20) FlutterPlatform platform,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "FLUTTER_MODULES", length = 1000) List<String> modules,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "FLUTTER_TEST_MODULES", length = 1000) List<String> testModules,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "FLUTTER_TEST_SUBMODULES", length = 1000) List<String> testSubmodules,
            @Convert(converter = DelimitedListConverter.Lines.class)
            @Column(name = "FLUTTER_TEST_SUBPLUGINS", length = 1000) List<String> testSubplugins,
            @Column(name = "FLUTTER_SIGNING_CREDENTIALS_ID", length = 200) String signingPasswordCredentialsId,
            @Column(name = "FLUTTER_PROD_LICENSE_CREDENTIALS_ID", length = 200) String prodLicenseCredentialsId,
            @Column(name = "FLUTTER_TEST_LICENSE_CREDENTIALS_ID", length = 200) String testLicenseCredentialsId,
            @Column(name = "FLUTTER_DELIVERY_GROUP", length = 200) String deliveryGroup,
            @Column(name = "FLUTTER_DELIVERY_ARTIFACT", length = 200) String deliveryArtifact,
            @Column(name = "FLUTTER_DELIVERY_PLUGIN", length = 300) String deliveryPlugin,
            @Column(name = "FLUTTER_SONAR_SOURCES", length = 500) String sonarSources,
            @Column(name = "FLUTTER_SONAR_TESTS", length = 500) String sonarTests,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "FLUTTER_SONAR_PLUGIN", nullable = false) Boolean sonarFlutterPlugin,
            @Column(name = "FLUTTER_DART_ANALYZE_COMMAND", length = 500) String dartAnalyzeCommand,
            @Column(name = "FLUTTER_SONAR_SCANNER_VERSION", length = 50) String sonarScannerVersion) {
    }
}
