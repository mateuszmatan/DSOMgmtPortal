package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings;
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
public class DemoDataLoader implements ApplicationRunner {

    static final String DEMO_JENKINS_URL = "https://jenkins.bbh.com";

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String JDK_17 = "/usr/lib/jvm/java-17-openjdk";
    private static final String BITBUCKET = "https://bitbucket.bbh.com/projects/%s/repos/%s";
    private static final String DEPLOY_SCRIPT = "scripts/deployment/zero-downtime-deployment.sh";
    private static final String VERSION_FILE = "scripts/deployment/version.properties";
    private static final String SMOKE_JENKINS = "https://jenkins-a.bbh.com/job/smoke/job/";
    private static final String REMOTE_TOKEN = "remote-jenkins-api-token";
    private static final String SONAR_BADGE = "sqb_95b6b9cfb2fe8f3fa0e45261ece1856d3fb1ebc";
    private static final String CERT_SCANNER_REPOSITORY = "https://bitbucket.bbh.com/projects/TA/repos/cert-scanner";
    private static final String CERT_SCANNER_DEPLOY_DIR = "/opt/ta/CertScanner/gui/deployment";

    private final ProductsUseCase products;
    private final DepartmentsUseCase departments;
    private final PipelinesUseCase pipelines;
    private final ManageGlobalSettingsUseCase settings;

    public DemoDataLoader(ProductsUseCase products, DepartmentsUseCase departments, PipelinesUseCase pipelines,
                          ManageGlobalSettingsUseCase settings) {
        this.products = products;
        this.departments = departments;
        this.pipelines = pipelines;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!products.list(null).isEmpty()) {
            return;
        }
        GlobalSettingsValues global = settings.current().values();
        if (global.platform().jenkinsUrl() == null) {
            settings.update(null, global.withPlatform(global.platform().withJenkinsUrl(DEMO_JENKINS_URL)));
        }

        Product certScanner = products.create(new ProductCommand(null, new ProductDetails("CERTSCANNER", "CertScanner",
                "Monitors the validity of TLS certificates across BBH and alerts owners before they expire.",
                "Technology Architecture", "ta-team@bbh.com", department("Corporate Technology")),
                new AppScanAccount("bbh_b81fbc9f-39c1-8eb4-38b5-b702268969b9", null),
                List.of(certScannerGui(), certScannerApi())));
        Product payments = products.create(new ProductCommand(null, new ProductDetails("PAYHUB", "Payments Hub",
                "Payment orchestration platform: gateway, ledger, notifications and reporting.",
                "Payments Engineering", "payments-eng@bbh.com", department("Fund Services")),
                new AppScanAccount("bbh_1c2d3e4f-0000-4abc-9def-123456789abc", "payhub-appscan-key-secret"), List.of(
                mavenOpenShift("gateway", "Public payment API", "3a1b2c3d-1111-4a5b-8c9d-0e1f2a3b4c5d",
                        "payhub-gateway", "payhub-gateway", "pay-payhub"),
                gradleVm("ledger", "Double-entry ledger", "3a1b2c3d-2222-4a5b-8c9d-0e1f2a3b4c5d", "payhub-ledger",
                        "payhub-ledger", "/opt/pay/PayHub/ledger/deployment"),
                gradleVm("notifications", "E-mail and push notifications", "3a1b2c3d-3333-4a5b-8c9d-0e1f2a3b4c5d",
                        "payhub-notifications", "payhub-notifications", "/opt/pay/PayHub/notifications/deployment"),
                flutter("mobile-app", "Flutter mobile application", "3a1b2c3d-4444-4a5b-8c9d-0e1f2a3b4c5d",
                        "payhub-mobile"))));

        String certScannerJobs = "DevSecOps/CertScanner-";
        for (String service : List.of("gui", "backend-api")) {
            pipeline(certScanner, service, PipelineType.FULL, List.of("linux-agent", "windows-agent"), null, null,
                    certScannerJobs + "pipeline");
            pipeline(certScanner, service, PipelineType.SECURITY, List.of("linux-agent"),
                    service.equals("gui") ? certScannerJobs + "extended-pipeline" : null, null,
                    certScannerJobs + "security-pipeline");
            pipeline(certScanner, service, PipelineType.EXTENDED, List.of("linux-agent"), null,
                    certScannerJobs + "security-pipeline", certScannerJobs + "extended-pipeline");
        }
        pipeline(certScanner, "gui", PipelineType.SAST);
        pipeline(payments, "gateway", PipelineType.FULL);
        pipeline(payments, "gateway", PipelineType.SECURITY);
        pipeline(payments, "gateway", PipelineType.EXTENDED);
        pipeline(payments, "ledger", PipelineType.FULL);
        pipeline(payments, "notifications", PipelineType.FULL);
        PipelineView retired = pipeline(payments, "mobile-app", PipelineType.SAST);
        pipelines.revokeKey(retired.pipeline().id(), "Mobile app moved to the new mobile platform pipeline");
        log.info("Created demo data: {} and {}", certScanner.name(), payments.name());
    }

    private long department(String name) {
        return departments.list().stream().filter(department -> department.name().equalsIgnoreCase(name))
                .findFirst().orElseGet(() -> departments.create(name)).id();
    }

    private static ServiceDraft certScannerGui() {
        String title = "CertScanner-GUI";
        TestJob remote = new TestJob(TestStage.SMOKE, null, TestJobType.REMOTE, null, 15, null, null, null,
                REMOTE_TOKEN, null, null, false, false, false, false, false, false);
        return new ServiceDraft(null, "gui", "Angular front end", new ServiceSettings(
                new BuildSettings(BuildTool.GRADLE, ".", JDK_17, false, null,
                        ToolCommand.of(List.of("clean", "build", "bootJar"), List.of("--refresh-dependencies"))),
                new UnitTestSettings(
                        ToolCommand.of(List.of("test", "jacocoTestReport", "jacocoTestCoverageVerification"), List.of()),
                        "build/test-results/test/*.xml", null, null, false, null),
                new TestSettings(null, 20, 5, null, true, true, true, null, null, null),
                List.of(TestJob.of(TestStage.REGRESSION, title + " - regression", TestJobType.LOCAL,
                                "cert-scanner/regression-tests", 60),
                        withParameters(TestJob.of(TestStage.REGRESSION, title + " - regression (certificates)",
                                TestJobType.LOCAL, "cert-scanner/regression-certificates", 60), "ENV=rd", null),
                        remote(remote, title + " - login smoke", SMOKE_JENKINS + "cert-scanner-login"),
                        remote(remote, title + " - certificate list smoke", SMOKE_JENKINS + "cert-scanner-list"),
                        withParameters(remote(remote, title + " - notification smoke",
                                "smoke/cert-scanner-notifications"), "ENV=rd", "jenkins-b"),
                        withType(remote(remote, title + " - local smoke", "cert-scanner/smoke-tests"),
                                TestJobType.LOCAL),
                        remote(remote, null, "https://jenkins-c.bbh.com/job/smoke/job/cert-scanner-dashboard"),
                        remote(remote, null, "https://jenkins-b.bbh.com/job/smoke/job/cert-scanner-expiry"),
                        TestJob.of(TestStage.PERFORMANCE, title + " - performance", TestJobType.LOCAL,
                                "cert-scanner/performance-tests", 120)),
                new DeploymentSettings(DeployTarget.VM, null, null, null),
                null,
                new UrbanCodeSettings("deploy.bbh.com", "tomcat-app-process", false, true, false, true, false, null,
                        null),
                List.of(UrbanCodeApplicationSettings.of(title, null, List.of(), null,
                        List.of(UrbanCodeComponent.of(title + "-app", "gui/build/libs", "*.jar")))),
                Map.of(Region.RD, new SshTarget("rdltaapps1.testbbh.com", "taadmin", CERT_SCANNER_DEPLOY_DIR,
                                DEPLOY_SCRIPT, VERSION_FILE),
                        Region.QC, new SshTarget("qcltaapps1.testbbh.com", "taadmin", CERT_SCANNER_DEPLOY_DIR,
                                DEPLOY_SCRIPT, VERSION_FILE)),
                null,
                certScannerAppScan("209f44ac-dd06-4ca0-884e-d944904f8020", "CertScanner-GUI",
                        "http://rdltaapps1.testbbh.com"),
                new SonarSettings("CertScanner-GUI", "cert-scanner-gui", "SonarQube", "sonarqube-token",
                        "sonarqube-token", SONAR_BADGE, true, true, ToolCommand.of(List.of("sonarqube"), List.of()),
                        null),
                new NexusIqSettings(null, null, "CertScanner-GUI-SCA"),
                List.of(NexusIqApplication.of("CertValidityMonitoring-GUI", List.of("**/gui/build/libs/*.jar"))),
                ScmSettings.of(CERT_SCANNER_REPOSITORY, "bitbucket-http-credentials"),
                GoldenFixPolicy.inherit(true),
                MetricsSettings.of(true, "CertScanner", "test"),
                null));
    }

    private static ServiceDraft certScannerApi() {
        String title = "CertScanner-Backend";
        String image = "docker-qc.tools.bbh.com/ta/certscanner-api";
        return new ServiceDraft(null, "backend-api", "REST API and certificate scanner", new ServiceSettings(
                new BuildSettings(BuildTool.MAVEN, ".", JDK_17, false, "target/*.jar",
                        new ToolCommand(List.of("clean", "verify"), List.of("-B", "-U"), null, null,
                                List.of("MAVEN_OPTS=-Xms512m -Xmx1g"), null, false)),
                new UnitTestSettings(ToolCommand.of(List.of("test", "jacoco:report"), List.of()),
                        "target/surefire-reports/*.xml", null, null, false, null),
                null,
                List.of(TestJob.of(TestStage.REGRESSION, title + " - regression", TestJobType.LOCAL,
                                "cert-scanner/api-regression-tests", 60),
                        TestJob.of(TestStage.SMOKE, title + " - smoke", TestJobType.LOCAL,
                                "cert-scanner/api-smoke-tests", 15),
                        new TestJob(TestStage.SMOKE, title + " - contract smoke", null,
                                SMOKE_JENKINS + "cert-scanner-api-contract", null, null, null, null, REMOTE_TOKEN,
                                null, null, false, false, false, false, false, false),
                        TestJob.of(TestStage.PERFORMANCE, title + " - performance", TestJobType.LOCAL,
                                "cert-scanner/api-performance-tests", 120)),
                new DeploymentSettings(DeployTarget.OPENSHIFT, "certscanner-api", "certscanner-api.jar", null),
                null, null, null, null,
                Map.of(Region.RD, new OpenShiftTarget("ta-certscanner-build", "openshift/buildconfig.yaml",
                                "openshift/Dockerfile", "target/docker", null, image, image, "/etc/pki/openshift",
                                "/home/jenkins/.docker/nexus-auth.json", "ta-certscanner-rd",
                                "openshift/deployment.yaml", "openshift/config-rd.yaml", false, "/actuator/health",
                                "certscanner-api-rd.apps.ocp-rd.testbbh.com", null, null, null, null, null, null),
                        Region.QC, new OpenShiftTarget(null, null, null, null, null, null, image, null, null,
                                "ta-certscanner-qc", "openshift/deployment.yaml", "openshift/config-qc.yaml", false,
                                "/actuator/health", "certscanner-api-qc.apps.ocp-qc.testbbh.com", null, null, null,
                                null, null, null)),
                certScannerAppScan("209f44ac-dd06-4ca0-884e-d944904f8021", "CertScanner-Backend",
                        "http://rdltaapps1.testbbh.com:8080/api"),
                new SonarSettings("CertScanner-Backend", "cert-scanner-backend", "SonarQube", null, "sonarqube-token",
                        SONAR_BADGE, true, false, ToolCommand.of(List.of("sonar:sonar"), List.of()), null),
                new NexusIqSettings(null, null, "CertScanner-Backend-SCA"),
                List.of(NexusIqApplication.of("CertValidityMonitoring-Backend", List.of("**/api/target/*.jar"))),
                ScmSettings.of(CERT_SCANNER_REPOSITORY, "bitbucket-http-credentials"),
                GoldenFixPolicy.inherit(true),
                MetricsSettings.of(true, "CertScanner", "test"),
                null));
    }

    private static AppScanSettings certScannerAppScan(String appScanId, String scanName, String dastTargetUrl) {
        return new AppScanSettings(appScanId, scanName + "-SAST", List.of(), List.of(), true, false, false, false,
                null, null, true, scanName + "-DAST", dastTargetUrl, null, null);
    }

    private static TestJob remote(TestJob defaults, String name, String job) {
        return new TestJob(defaults.stage(), name, defaults.type(), job, defaults.timeoutMinutes(), null, null, null,
                defaults.credentialsId(), null, null, false, false, false, false, false, false);
    }

    private static TestJob withParameters(TestJob job, String parameters, String remoteJenkins) {
        return new TestJob(job.stage(), job.name(), job.type(), job.job(), job.timeoutMinutes(), parameters,
                remoteJenkins, null, job.credentialsId(), null, null, false, false, false, false, false, false);
    }

    private static TestJob withType(TestJob job, TestJobType type) {
        return new TestJob(job.stage(), job.name(), type, job.job(), job.timeoutMinutes(), job.parameters(), null,
                null, job.credentialsId(), null, null, false, false, false, false, false, false);
    }

    private static ServiceDraft gradleVm(String name, String description, String appScanId, String sonarKey,
                                         String repo, String deployDir) {
        String title = sonarKey.toUpperCase();
        return new ServiceDraft(null, name, description, new ServiceSettings(
                new BuildSettings(BuildTool.GRADLE, ".", JDK_17, false, "build/libs/*.jar",
                        ToolCommand.of(List.of("clean", "build", "bootJar"), List.of("--refresh-dependencies"))),
                new UnitTestSettings(ToolCommand.of(List.of("test", "jacocoTestReport"), List.of()),
                        "build/test-results/test/*.xml", null, null, false, null),
                new TestSettings(null, 10, 5, null, true, true, true, null, null, null),
                testJobs(title, repo),
                new DeploymentSettings(DeployTarget.VM, null, null, null),
                null,
                UrbanCodeSettings.DEFAULTS,
                List.of(UrbanCodeApplicationSettings.of(title, 1, List.of("DV", "RD"), null,
                        List.of(UrbanCodeComponent.of(title + "-app", "build/libs", "*.jar")))),
                Map.of(Region.RD, new SshTarget(null, null, deployDir, DEPLOY_SCRIPT, null),
                        Region.QC, new SshTarget(null, null, deployDir, DEPLOY_SCRIPT, null)),
                null,
                appScan(appScanId, sonarKey),
                SonarSettings.of(title, sonarKey, ToolCommand.of(List.of("sonarqube"), List.of())),
                null,
                List.of(NexusIqApplication.of(sonarKey, List.of("**/build/libs/*.jar"))),
                ScmSettings.of(BITBUCKET.formatted("PAY", repo), "bitbucket-http-credentials"),
                GoldenFixPolicy.INHERITED,
                MetricsSettings.of(true, null, "test"),
                null));
    }

    private static ServiceDraft mavenOpenShift(String name, String description, String appScanId, String sonarKey,
                                               String repo, String namespace) {
        String title = sonarKey.toUpperCase();
        String image = "docker-qc.tools.bbh.com/" + namespace + "/" + name;
        return new ServiceDraft(null, name, description, new ServiceSettings(
                new BuildSettings(BuildTool.MAVEN, ".", JDK_17, false, "target/*.jar",
                        new ToolCommand(List.of("clean", "verify"), List.of("-B", "-U"), null, null,
                                List.of("MAVEN_OPTS=-Xms512m -Xmx1g"), null, false)),
                new UnitTestSettings(ToolCommand.of(List.of("test", "jacoco:report"), List.of()),
                        "target/surefire-reports/*.xml", null, null, false, null),
                null,
                testJobs(title, repo),
                new DeploymentSettings(DeployTarget.OPENSHIFT, sonarKey, sonarKey + ".jar", null),
                null, null, null, null,
                Map.of(Region.RD, new OpenShiftTarget(namespace + "-build", "openshift/buildconfig.yaml",
                                "openshift/Dockerfile", "target/docker", null, image, image, "/etc/pki/openshift",
                                "/home/jenkins/.docker/nexus-auth.json", namespace + "-rd", "openshift/deployment.yaml",
                                "openshift/config-rd.yaml", false, "/actuator/health",
                                sonarKey + "-rd.apps.ocp-rd.testbbh.com", null, null, null, null, null, null),
                        Region.QC, new OpenShiftTarget(null, null, null, null, null, null, image, null, null,
                                namespace + "-qc", "openshift/deployment.yaml", "openshift/config-qc.yaml", false,
                                "/actuator/health", sonarKey + "-qc.apps.ocp-qc.testbbh.com", null, null, null, null,
                                null, null)),
                appScan(appScanId, sonarKey),
                SonarSettings.of(title, sonarKey, ToolCommand.of(List.of("sonar:sonar"), List.of())),
                null,
                List.of(NexusIqApplication.of(sonarKey, List.of("**/target/*.jar"))),
                ScmSettings.of(BITBUCKET.formatted("PAY", repo), "bitbucket-http-credentials"),
                GoldenFixPolicy.INHERITED,
                MetricsSettings.of(true, null, "test"),
                null));
    }

    private static ServiceDraft flutter(String name, String description, String appScanId, String repo) {
        return new ServiceDraft(null, name, description, new ServiceSettings(
                new BuildSettings(BuildTool.FLUTTER, ".", JDK_17, false, null, null),
                null, null, List.of(),
                new DeploymentSettings(DeployTarget.VM, null, null, null),
                null, null, null, null, null,
                AppScanSettings.of(appScanId),
                null,
                null,
                null,
                ScmSettings.of(BITBUCKET.formatted("PAY", repo), "bitbucket-http-credentials"),
                GoldenFixPolicy.inherit(false),
                MetricsSettings.of(true, null, "test"),
                new FlutterSettings(FlutterPlatform.APK, List.of("core", "payments"), List.of("core", "payments"),
                        List.of("core", "payments"), List.of("secure_storage"), "payhub-mobile-signing-password",
                        "payhub-mobile-prod-licence", "payhub-mobile-test-licence", "com.bbh.payhub", "payhub-mobile",
                        "org.apache.maven.plugins:maven-deploy-plugin:3.1.2:deploy-file", "lib", "test", false, null,
                        null)));
    }

    private static AppScanSettings appScan(String appScanId, String scanName) {
        return new AppScanSettings(appScanId, scanName + "-sast", List.of(), List.of("node_modules"), true, false,
                false, false, null, null, true, scanName + "-dast", "http://rdltaapps1.testbbh.com", null, null);
    }

    private static List<TestJob> testJobs(String title, String repo) {
        return List.of(
                TestJob.of(TestStage.SMOKE, title + " - smoke", TestJobType.LOCAL, repo + "/smoke-tests", 15),
                new TestJob(TestStage.SMOKE, title + " - login smoke", null, SMOKE_JENKINS + repo + "-login", null,
                        null, null, null, REMOTE_TOKEN, null, null, false, false, false, false, false, false),
                withParameters(TestJob.of(TestStage.REGRESSION, title + " - regression", TestJobType.LOCAL,
                        repo + "/regression-tests", 60), "ENV=rd", null),
                TestJob.of(TestStage.PERFORMANCE, title + " - performance", TestJobType.LOCAL,
                        repo + "/performance-tests", 120));
    }

    private PipelineView pipeline(Product product, String serviceName, PipelineType type) {
        String folder = "DevSecOps/" + product.code() + "/";
        return pipeline(product, serviceName, type, List.of(PipelineSettings.DEFAULT_AGENT_LABEL),
                type == PipelineType.SECURITY ? folder + serviceName + "-extended" : null,
                type == PipelineType.EXTENDED ? folder + serviceName + "-security" : null,
                folder + serviceName + "-" + type.variant());
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
}
