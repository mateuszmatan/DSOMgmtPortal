package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings;
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings;
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication;
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings;
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings;
import com.bbh.itss.dso.portal.domain.catalog.SshTarget;
import com.bbh.itss.dso.portal.domain.catalog.TestJob;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestSettings;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand;
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.adapter.in.startup.DemoDataLoader.Stack.FLUTTER;
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoDataLoader.Stack.GRADLE_OPENSHIFT;
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoDataLoader.Stack.GRADLE_VM;
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoDataLoader.Stack.MAVEN_OPENSHIFT;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT;
import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APK;
import static com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy.INHERITED;
import static com.bbh.itss.dso.portal.domain.catalog.Region.QC;
import static com.bbh.itss.dso.portal.domain.catalog.Region.RD;
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.LOCAL;
import static com.bbh.itss.dso.portal.domain.catalog.TestJobType.REMOTE;
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.PERFORMANCE;
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.REGRESSION;
import static com.bbh.itss.dso.portal.domain.catalog.TestStage.SMOKE;
import static com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings.DEFAULTS;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings.DEFAULT_AGENT_LABEL;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST;
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SECURITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.UUID.nameUUIDFromBytes;
import static java.util.stream.Collectors.toSet;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
@RequiredArgsConstructor
@Slf4j
public class DemoDataLoader implements ApplicationRunner {

    static final String DEMO_JENKINS_URL = "https://jenkins.bbh.com";
    static final String CERT_SCANNER = "CERTSCANNER";
    static final List<DemoProduct> CATALOGUE = List.of(
            new DemoProduct("DOCSENSE", "DocSense", "AI Lab", "AIL", "AI Lab Engineering",
                    "Reads fund prospectuses and KYC documents and extracts their data with machine learning.",
                    service("extraction-api", "Document extraction REST API", GRADLE_OPENSHIFT, SECURITY, EXTENDED),
                    service("review-ui", "Review screen for analysts", GRADLE_VM, SAST)),
            new DemoProduct("ADVISORAI", "Advisor Assistant", "AI Lab", "AIL", "AI Lab Engineering",
                    "Drafts answers for relationship managers from approved BBH content with a language model.",
                    service("assistant-api", "Conversation API", MAVEN_OPENSHIFT, SECURITY),
                    service("content-indexer", "Indexes approved content", GRADLE_OPENSHIFT)),
            new DemoProduct("DEALFLOW", "DealFlow", "Capital Partners", "CPD", "Private Equity Technology",
                    "Private equity deal pipeline and investment committee workflow.",
                    service("deals-web", "Deal team web client", GRADLE_VM),
                    service("deals-api", "Deal and committee API", MAVEN_OPENSHIFT, SECURITY, EXTENDED)),
            new DemoProduct("LPPORTAL", "LP Portal", "Capital Partners", "CPD", "Investor Reporting",
                    "Reporting portal for limited partners: capital calls, distributions and statements.",
                    service("portal-web", "Investor web portal", GRADLE_OPENSHIFT),
                    service("statements", "Statement generation", MAVEN_OPENSHIFT, SAST),
                    service("lp-mobile", "Investor mobile application", FLUTTER, SAST)),
            new DemoProduct("ACCESSHUB", "Access Hub", "Corporate Technology", "CT", "Identity and Access",
                    "Self-service access requests and quarterly entitlement reviews.",
                    service("requests-ui", "Access request screens", GRADLE_VM),
                    service("workflow", "Approval workflow engine", MAVEN_OPENSHIFT, SECURITY)),
            new DemoProduct("SAFEKEEP", "Safekeeping Ledger", "Custody", "CUS", "Custody Platform",
                    "Books and reconciles client positions held with sub-custodians.",
                    service("positions-api", "Positions and holdings API", MAVEN_OPENSHIFT, SECURITY, EXTENDED),
                    service("recon-batch", "Nightly reconciliation", GRADLE_VM, SAST)),
            new DemoProduct("CORPACT", "Corporate Actions", "Custody", "CUS", "Asset Servicing",
                    "Captures corporate action events and collects client elections.",
                    service("events-api", "Event capture API", GRADLE_OPENSHIFT, SECURITY),
                    service("elections-ui", "Client election screens", GRADLE_VM)),
            new DemoProduct("PAYHUB", "Payments Hub", "Fund Services", "PAY", "Payments Engineering",
                    "Payment orchestration platform: gateway, ledger, notifications and reporting.",
                    service("gateway", "Public payment API", MAVEN_OPENSHIFT, SECURITY, EXTENDED),
                    service("ledger", "Double-entry ledger", GRADLE_VM),
                    service("notifications", "E-mail and push notifications", GRADLE_VM),
                    service("mobile-app", "Flutter mobile application", FLUTTER, SAST)),
            new DemoProduct("NAVCALC", "NAV Calculator", "Fund Services", "FS", "Fund Accounting",
                    "Daily net asset value calculation and pricing for fund administration clients.",
                    service("pricing-engine", "Security pricing engine", MAVEN_OPENSHIFT, SECURITY),
                    service("nav-api", "NAV publication API", GRADLE_OPENSHIFT, EXTENDED)));
    static final Map<String, String> RETIRED = Map.of(
            "PAYHUB mobile-app SAST", "Mobile app moved to the new mobile platform pipeline",
            "SAFEKEEP recon-batch SAST", "Reconciliation moved to the mainframe scheduler");

    private static final String JDK_17 = "/usr/lib/jvm/java-17-openjdk";
    private static final String BITBUCKET = "https://bitbucket.bbh.com/projects/%s/repos/%s";
    private static final String DEPLOY_SCRIPT = "scripts/deployment/zero-downtime-deployment.sh";
    private static final String VERSION_FILE = "scripts/deployment/version.properties";
    private static final String SMOKE_JENKINS = "https://jenkins-a.bbh.com/job/smoke/job/";
    private static final String REMOTE_TOKEN = "remote-jenkins-api-token";
    private static final String SONAR_BADGE = "sqb_95b6b9cfb2fe8f3fa0e45261ece1856d3fb1ebc";
    private static final String CERT_SCANNER_REPOSITORY = "https://bitbucket.bbh.com/projects/TA/repos/cert-scanner";
    private static final String CERT_SCANNER_DEPLOY_DIR = "/opt/ta/CertScanner/gui/deployment";
    private static final String BITBUCKET_CREDENTIALS = "bitbucket-http-credentials";

    private final ProductsUseCase products;
    private final DepartmentsUseCase departments;
    private final PipelinesUseCase pipelines;
    private final ManageGlobalSettingsUseCase settings;

    @Override
    public void run(ApplicationArguments args) {
        Set<String> present = products.list(null).stream().map(ProductSummaryView::code).collect(toSet());
        Set<String> demo = Stream.concat(Stream.of(CERT_SCANNER), CATALOGUE.stream().map(DemoProduct::code))
                .collect(toSet());
        if (!demo.containsAll(present) || present.containsAll(demo)) {
            return;
        }
        GlobalSettingsValues global = settings.current().values();
        if (global.platform().jenkinsUrl() == null) {
            settings.update(null, global.withPlatform(global.platform().withJenkinsUrl(DEMO_JENKINS_URL)));
        }
        if (!present.contains(CERT_SCANNER)) {
            certScanner();
        }
        CATALOGUE.stream().filter(product -> !present.contains(product.code())).forEach(this::create);
        log.info("Created the demo catalogue: {} products in {} departments", demo.size() - present.size(),
                CATALOGUE.stream().map(DemoProduct::department).distinct().count());
    }

    private void certScanner() {
        Product certScanner = products.create(new ProductCommand(null, new ProductDetails(CERT_SCANNER, "CertScanner",
                "Monitors the validity of TLS certificates across BBH and alerts owners before they expire.",
                "Technology Architecture", "ta-team@bbh.com", department("Corporate Technology")),
                new AppScanAccount("bbh_b81fbc9f-39c1-8eb4-38b5-b702268969b9", null),
                List.of(certScannerGui(), certScannerApi()), null));
        String jobs = "DevSecOps/CertScanner-";
        for (String service : List.of("gui", "backend-api")) {
            pipeline(certScanner, service, FULL, List.of("linux-agent", "windows-agent"), null, null,
                    jobs + "pipeline");
            pipeline(certScanner, service, SECURITY, List.of("linux-agent"),
                    service.equals("gui") ? jobs + "extended-pipeline" : null, null, jobs + "security-pipeline");
            pipeline(certScanner, service, EXTENDED, List.of("linux-agent"), null, jobs + "security-pipeline",
                    jobs + "extended-pipeline");
        }
        pipeline(certScanner, "gui", SAST, List.of());
    }

    private void create(DemoProduct demo) {
        String team = demo.team().toLowerCase(ROOT).replaceAll("[^a-z]+", "-");
        Product product = products.create(new ProductCommand(null, new ProductDetails(demo.code(), demo.name(),
                demo.description(), demo.team(), team + "@bbh.com", department(demo.department())),
                new AppScanAccount("bbh_" + uuid(demo.code()), demo.code().toLowerCase(ROOT) + "-appscan-key"),
                demo.services().stream().map(service -> draft(demo, service)).toList(), null));
        for (DemoService service : demo.services()) {
            Stream.concat(Stream.of(FULL), service.types().stream()).forEach(type -> {
                PipelineView created = pipeline(product, service.name(), type, service.types());
                String retired = RETIRED.get(demo.code() + " " + service.name() + " " + type);
                if (retired != null) {
                    pipelines.revokeKey(created.pipeline().id(), retired);
                }
            });
        }
    }

    private long department(String name) {
        return departments.list().stream().filter(department -> department.name().equalsIgnoreCase(name))
                .findFirst().orElseGet(() -> departments.create(name)).id();
    }

    private static ServiceDraft draft(DemoProduct product, DemoService service) {
        String key = product.code().toLowerCase(ROOT) + "-" + service.name();
        String repository = BITBUCKET.formatted(product.bitbucketProject(), key);
        return new ServiceDraft(null, service.name(), service.description(), switch (service.stack()) {
            case GRADLE_VM -> vm(key, repository);
            case GRADLE_OPENSHIFT -> openShift(GRADLE, key, repository, product.bitbucketProject());
            case MAVEN_OPENSHIFT -> openShift(MAVEN, key, repository, product.bitbucketProject());
            case FLUTTER -> flutter(product, key, repository);
        });
    }

    private static ServiceDraft certScannerGui() {
        String title = "CertScanner-GUI";
        return new ServiceDraft(null, "gui", "Angular front end", ServiceSettings.builder()
                .build(new BuildSettings(GRADLE, ".", JDK_17, false, null,
                        ToolCommand.of(List.of("clean", "build", "bootJar"), List.of("--refresh-dependencies"))))
                .unitTests(UnitTestSettings.builder()
                        .command(ToolCommand.of(List.of("test", "jacocoTestReport", "jacocoTestCoverageVerification"),
                                List.of()))
                        .resultPattern("build/test-results/test/*.xml").allowEmptyResults(false).build())
                .tests(TestSettings.builder().smokeMaxParallel(20).regressionMaxParallel(5).smokeRequired(true)
                        .regressionRequired(true).performanceRequired(true).build())
                .testJobs(List.of(
                        TestJob.of(REGRESSION, title + " - regression", LOCAL, "cert-scanner/regression-tests", 60),
                        job(REGRESSION, title + " - regression (certificates)", LOCAL,
                                "cert-scanner/regression-certificates", 60, "ENV=rd", null, null),
                        smoke(title + " - login smoke", REMOTE, SMOKE_JENKINS + "cert-scanner-login"),
                        smoke(title + " - certificate list smoke", REMOTE, SMOKE_JENKINS + "cert-scanner-list"),
                        job(SMOKE, title + " - notification smoke", REMOTE,
                                "smoke/cert-scanner-notifications", 15, "ENV=rd", "jenkins-b", REMOTE_TOKEN),
                        smoke(title + " - local smoke", LOCAL, "cert-scanner/smoke-tests"),
                        smoke(null, REMOTE, "https://jenkins-c.bbh.com/job/smoke/job/cert-scanner-dashboard"),
                        smoke(null, REMOTE, "https://jenkins-b.bbh.com/job/smoke/job/cert-scanner-expiry"),
                        TestJob.of(PERFORMANCE, title + " - performance", LOCAL,
                                "cert-scanner/performance-tests", 120)))
                .deployment(new DeploymentSettings(VM, null, null, null))
                .urbanCode(UrbanCodeSettings.builder().siteName("deploy.bbh.com").deployProcess("tomcat-app-process")
                        .skipWait(false).deployWithSnapshot(true).updateSnapshotComponents(false)
                        .includeOnlyDeployVersions(true).deployOnlyChanged(false).build())
                .urbanCodeApplications(List.of(UrbanCodeApplicationSettings.of(title, null, List.of(), null,
                        List.of(UrbanCodeComponent.of(title + "-app", "gui/build/libs", "*.jar")))))
                .sshTargets(Map.of(
                        RD, SshTarget.builder().host("rdltaapps1.testbbh.com").user("taadmin")
                                .deployDir(CERT_SCANNER_DEPLOY_DIR).deployScript(DEPLOY_SCRIPT)
                                .versionFile(VERSION_FILE).build(),
                        QC, SshTarget.builder().host("qcltaapps1.testbbh.com").user("taadmin")
                                .deployDir(CERT_SCANNER_DEPLOY_DIR).deployScript(DEPLOY_SCRIPT)
                                .versionFile(VERSION_FILE).build()))
                .appScan(appScan("209f44ac-dd06-4ca0-884e-d944904f8020", "CertScanner-GUI-SAST", "CertScanner-GUI-DAST",
                        List.of(), "http://rdltaapps1.testbbh.com"))
                .sonar(SonarSettings.builder().projectName("CertScanner-GUI").projectKey("cert-scanner-gui")
                        .installationName("SonarQube").credentialsId("sonarqube-token")
                        .authTokenCredentialsId("sonarqube-token").badgeToken(SONAR_BADGE).addBadges(true)
                        .fullBadges(true).command(ToolCommand.of(List.of("sonarqube"), List.of())).build())
                .nexusIq(new NexusIqSettings(null, null, "CertScanner-GUI-SCA"))
                .nexusIqApplications(List.of(NexusIqApplication.of("CertValidityMonitoring-GUI",
                        List.of("**/gui/build/libs/*.jar"))))
                .scm(ScmSettings.of(CERT_SCANNER_REPOSITORY, "bitbucket-http-credentials"))
                .goldenFix(GoldenFixPolicy.inherit(true))
                .metrics(MetricsSettings.of(true, "CertScanner", "test"))
                .build());
    }

    private static ServiceDraft certScannerApi() {
        String title = "CertScanner-Backend";
        String image = "docker-qc.tools.bbh.com/ta/certscanner-api";
        return new ServiceDraft(null, "backend-api", "REST API and certificate scanner", ServiceSettings.builder()
                .build(new BuildSettings(MAVEN, ".", JDK_17, false, "target/*.jar",
                        ToolCommand.builder().tasks(List.of("clean", "verify")).flags(List.of("-B", "-U"))
                                .environment(List.of("MAVEN_OPTS=-Xms512m -Xmx1g")).returnStdout(false).build()))
                .unitTests(UnitTestSettings.builder()
                        .command(ToolCommand.of(List.of("test", "jacoco:report"), List.of()))
                        .resultPattern("target/surefire-reports/*.xml").allowEmptyResults(false).build())
                .testJobs(List.of(
                        TestJob.of(REGRESSION, title + " - regression", LOCAL, "cert-scanner/api-regression-tests", 60),
                        TestJob.of(SMOKE, title + " - smoke", LOCAL, "cert-scanner/api-smoke-tests", 15),
                        job(SMOKE, title + " - contract smoke", null,
                                SMOKE_JENKINS + "cert-scanner-api-contract", null, null, null, REMOTE_TOKEN),
                        TestJob.of(PERFORMANCE, title + " - performance", LOCAL,
                                "cert-scanner/api-performance-tests", 120)))
                .deployment(new DeploymentSettings(OPENSHIFT, "certscanner-api", "certscanner-api.jar", null))
                .openShiftTargets(openShiftTargets("ta-certscanner", image, "certscanner-api", "target/docker"))
                .appScan(appScan("209f44ac-dd06-4ca0-884e-d944904f8021", "CertScanner-Backend-SAST",
                        "CertScanner-Backend-DAST", List.of(), "http://rdltaapps1.testbbh.com:8080/api"))
                .sonar(SonarSettings.builder().projectName("CertScanner-Backend").projectKey("cert-scanner-backend")
                        .installationName("SonarQube").authTokenCredentialsId("sonarqube-token")
                        .badgeToken(SONAR_BADGE).addBadges(true).fullBadges(false)
                        .command(ToolCommand.of(List.of("sonar:sonar"), List.of())).build())
                .nexusIq(new NexusIqSettings(null, null, "CertScanner-Backend-SCA"))
                .nexusIqApplications(List.of(NexusIqApplication.of("CertValidityMonitoring-Backend",
                        List.of("**/api/target/*.jar"))))
                .scm(ScmSettings.of(CERT_SCANNER_REPOSITORY, "bitbucket-http-credentials"))
                .goldenFix(GoldenFixPolicy.inherit(true))
                .metrics(MetricsSettings.of(true, "CertScanner", "test"))
                .build());
    }

    private static TestJob smoke(String name, TestJobType type, String job) {
        return job(SMOKE, name, type, job, 15, null, null, REMOTE_TOKEN);
    }

    private static TestJob job(TestStage stage, String name, TestJobType type, String job, Integer timeoutMinutes,
                               String parameters, String remoteJenkins, String credentialsId) {
        return TestJob.builder().stage(stage).name(name).type(type).job(job).timeoutMinutes(timeoutMinutes)
                .parameters(parameters).remoteJenkins(remoteJenkins).credentialsId(credentialsId)
                .abortTriggeredJob(false).overrideTrustAllCertificates(false).preventRemoteBuildQueue(false)
                .trustAllCertificates(false).useCrumbCache(false).useJobInfoCache(false).build();
    }

    private static ServiceSettings vm(String key, String repository) {
        String title = key.toUpperCase(ROOT);
        String deployDir = "/opt/" + key.replace('-', '/') + "/deployment";
        return ServiceSettings.builder()
                .build(new BuildSettings(GRADLE, ".", JDK_17, false, "build/libs/*.jar",
                        ToolCommand.of(List.of("clean", "build", "bootJar"), List.of("--refresh-dependencies"))))
                .unitTests(UnitTestSettings.builder()
                        .command(ToolCommand.of(List.of("test", "jacocoTestReport"), List.of()))
                        .resultPattern("build/test-results/test/*.xml").allowEmptyResults(false).build())
                .tests(TestSettings.builder().smokeMaxParallel(10).regressionMaxParallel(5).smokeRequired(true)
                        .regressionRequired(true).performanceRequired(true).build())
                .testJobs(testJobs(title, key))
                .deployment(new DeploymentSettings(VM, null, null, null))
                .urbanCode(DEFAULTS)
                .urbanCodeApplications(List.of(UrbanCodeApplicationSettings.of(title, 1, List.of("DV", "RD"), null,
                        List.of(UrbanCodeComponent.of(title + "-app", "build/libs", "*.jar")))))
                .sshTargets(Map.of(RD, SshTarget.builder().deployDir(deployDir).deployScript(DEPLOY_SCRIPT).build(),
                        QC, SshTarget.builder().deployDir(deployDir).deployScript(DEPLOY_SCRIPT).build()))
                .appScan(appScan(uuid(key), key + "-sast", key + "-dast", List.of("node_modules"),
                        "http://rdltaapps1.testbbh.com"))
                .sonar(SonarSettings.of(title, key, ToolCommand.of(List.of("sonarqube"), List.of())))
                .nexusIqApplications(List.of(NexusIqApplication.of(key, List.of("**/build/libs/*.jar"))))
                .scm(ScmSettings.of(repository, BITBUCKET_CREDENTIALS))
                .goldenFix(INHERITED)
                .metrics(MetricsSettings.of(true, null, "test"))
                .build();
    }

    private static ServiceSettings openShift(BuildTool tool, String key, String repository, String project) {
        boolean maven = tool == MAVEN;
        String title = key.toUpperCase(ROOT);
        String namespace = project.toLowerCase(ROOT) + "-" + key;
        String image = "docker-qc.tools.bbh.com/" + namespace + "/" + key;
        return ServiceSettings.builder()
                .build(new BuildSettings(tool, ".", JDK_17, false, maven ? "target/*.jar" : "build/libs/*.jar", maven
                        ? ToolCommand.builder().tasks(List.of("clean", "verify")).flags(List.of("-B", "-U"))
                                .environment(List.of("MAVEN_OPTS=-Xms512m -Xmx1g")).returnStdout(false).build()
                        : ToolCommand.of(List.of("clean", "build", "bootJar"), List.of("--refresh-dependencies"))))
                .unitTests(UnitTestSettings.builder()
                        .command(ToolCommand.of(maven ? List.of("test", "jacoco:report")
                                : List.of("test", "jacocoTestReport"), List.of()))
                        .resultPattern(maven ? "target/surefire-reports/*.xml" : "build/test-results/test/*.xml")
                        .allowEmptyResults(false).build())
                .testJobs(testJobs(title, key))
                .deployment(new DeploymentSettings(OPENSHIFT, key, key + ".jar", null))
                .openShiftTargets(openShiftTargets(namespace, image, key, maven ? "target/docker" : "build/docker"))
                .appScan(appScan(uuid(key), key + "-sast", key + "-dast", List.of("node_modules"),
                        "http://rdltaapps1.testbbh.com"))
                .sonar(SonarSettings.of(title, key, ToolCommand.of(List.of(maven ? "sonar:sonar" : "sonarqube"),
                        List.of())))
                .nexusIqApplications(List.of(NexusIqApplication.of(key,
                        List.of(maven ? "**/target/*.jar" : "**/build/libs/*.jar"))))
                .scm(ScmSettings.of(repository, BITBUCKET_CREDENTIALS))
                .goldenFix(INHERITED)
                .metrics(MetricsSettings.of(true, null, "test"))
                .build();
    }

    private static Map<Region, OpenShiftTarget> openShiftTargets(String namespace, String image, String route,
                                                                 String dockerDir) {
        return Map.of(RD, OpenShiftTarget.builder().projectBuild(namespace + "-build")
                        .buildConfigPath("openshift/buildconfig.yaml").dockerFilePath("openshift/Dockerfile")
                        .buildContext(dockerDir).dockerRepoPush(image).dockerRepoPull(image)
                        .certDir("/etc/pki/openshift").nexusAuthFile("/home/jenkins/.docker/nexus-auth.json")
                        .projectDeployment(namespace + "-rd").deployConfigPath("openshift/deployment.yaml")
                        .configPath("openshift/config-rd.yaml").skipConfigDeploy(false)
                        .healthCheckUrl("/actuator/health").routeHostname(route + "-rd.apps.ocp-rd.testbbh.com")
                        .build(),
                QC, OpenShiftTarget.builder().dockerRepoPull(image).projectDeployment(namespace + "-qc")
                        .deployConfigPath("openshift/deployment.yaml").configPath("openshift/config-qc.yaml")
                        .skipConfigDeploy(false).healthCheckUrl("/actuator/health")
                        .routeHostname(route + "-qc.apps.ocp-qc.testbbh.com").build());
    }

    private static ServiceSettings flutter(DemoProduct product, String key, String repository) {
        String code = product.code().toLowerCase(ROOT);
        return ServiceSettings.builder()
                .build(new BuildSettings(BuildTool.FLUTTER, ".", JDK_17, false, null, null))
                .testJobs(List.of())
                .deployment(new DeploymentSettings(VM, null, null, null))
                .appScan(AppScanSettings.of(uuid(key)))
                .scm(ScmSettings.of(repository, BITBUCKET_CREDENTIALS))
                .goldenFix(GoldenFixPolicy.inherit(false))
                .metrics(MetricsSettings.of(true, null, "test"))
                .flutter(FlutterSettings.builder().platform(APK).modules(List.of("core", code))
                        .testModules(List.of("core", code)).testSubmodules(List.of("core", code))
                        .testSubplugins(List.of("secure_storage"))
                        .signingPasswordCredentialsId(key + "-signing-password")
                        .prodLicenseCredentialsId(key + "-prod-licence").testLicenseCredentialsId(key + "-test-licence")
                        .deliveryGroup("com.bbh." + code).deliveryArtifact(key)
                        .deliveryPlugin("org.apache.maven.plugins:maven-deploy-plugin:3.1.2:deploy-file")
                        .sonarSources("lib").sonarTests("test").sonarFlutterPlugin(false).build())
                .build();
    }

    private static AppScanSettings appScan(String applicationId, String sastScan, String dastScan,
                                           List<String> excludedDirs, String dastTargetUrl) {
        return AppScanSettings.builder().applicationId(applicationId).sastScanName(sastScan).includedDirs(List.of())
                .excludedDirs(excludedDirs).compile(true).sourceCodeOnly(false).useConfigFile(false).insecureTls(false)
                .dastEnabled(true).dastScanName(dastScan).dastTargetUrl(dastTargetUrl).build();
    }

    private static List<TestJob> testJobs(String title, String repo) {
        return List.of(
                TestJob.of(SMOKE, title + " - smoke", LOCAL, repo + "/smoke-tests", 15),
                job(SMOKE, title + " - login smoke", null, SMOKE_JENKINS + repo + "-login", null, null,
                        null, REMOTE_TOKEN),
                job(REGRESSION, title + " - regression", LOCAL, repo + "/regression-tests", 60, "ENV=rd", null, null),
                TestJob.of(PERFORMANCE, title + " - performance", LOCAL, repo + "/performance-tests", 120));
    }

    private static String uuid(String name) {
        return nameUUIDFromBytes(name.getBytes(UTF_8)).toString();
    }

    private static DemoService service(String name, String description, Stack stack, PipelineType... types) {
        return new DemoService(name, description, stack, List.of(types));
    }

    private PipelineView pipeline(Product product, String serviceName, PipelineType type,
                                  List<PipelineType> siblings) {
        String job = "DevSecOps/" + product.code() + "/" + serviceName + "-";
        return pipeline(product, serviceName, type, List.of(DEFAULT_AGENT_LABEL),
                type == SECURITY && siblings.contains(EXTENDED) ? job + EXTENDED.variant() : null,
                type == EXTENDED && siblings.contains(SECURITY) ? job + SECURITY.variant() : null,
                job + type.variant());
    }

    private PipelineView pipeline(Product product, String serviceName, PipelineType type, List<String> agentLabels,
                                  String extendedJob, String securityJob, String jenkinsJob) {
        Service service = product.services().stream().filter(s -> s.name().equals(serviceName)).findFirst()
                .orElseThrow();
        PipelineSettings configured = new PipelineSettings(agentLabels, extendedJob, securityJob, jenkinsJob, null);
        return started(product, service, type)
                .map(started -> pipelines.update(started.pipeline().id(), type, configured))
                .orElseGet(() -> pipelines.create(service.id(), type, configured));
    }

    private Optional<PipelineView> started(Product product, Service service, PipelineType type) {
        return pipelines.listForProduct(product.id()).stream()
                .filter(view -> view.service().id().equals(service.id()))
                .flatMap(view -> view.pipelines().stream())
                .filter(view -> view.pipeline().type() == type)
                .findFirst();
    }

    enum Stack {
        GRADLE_VM, GRADLE_OPENSHIFT, MAVEN_OPENSHIFT, FLUTTER
    }

    record DemoService(String name, String description, Stack stack, List<PipelineType> types) {
    }

    record DemoProduct(String code, String name, String department, String bitbucketProject, String team,
                       String description, List<DemoService> services) {

        DemoProduct(String code, String name, String department, String bitbucketProject, String team,
                    String description, DemoService... services) {
            this(code, name, department, bitbucketProject, team, description, List.of(services));
        }
    }
}
