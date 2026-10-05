package com.bbh.itss.dso.portal.demo;

import com.bbh.itss.dso.portal.adapter.in.web.GoldenFixPolicyDto;
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.dso.portal.application.settings.port.in.UpdateGlobalSettingsCommand;
import com.bbh.itss.dso.portal.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.catalog.BuildSettings;
import com.bbh.itss.dso.portal.catalog.DeploymentSettings;
import com.bbh.itss.dso.portal.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.catalog.FlutterSettings;
import com.bbh.itss.dso.portal.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.catalog.NexusIqSettings;
import com.bbh.itss.dso.portal.catalog.OpenShiftTarget;
import com.bbh.itss.dso.portal.catalog.ProductCatalogService;
import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.catalog.ProductRequest;
import com.bbh.itss.dso.portal.catalog.ProductResponse;
import com.bbh.itss.dso.portal.catalog.ScmSettings;
import com.bbh.itss.dso.portal.catalog.ServiceRequest;
import com.bbh.itss.dso.portal.catalog.ServiceResponse;
import com.bbh.itss.dso.portal.catalog.SonarSettings;
import com.bbh.itss.dso.portal.catalog.SshTarget;
import com.bbh.itss.dso.portal.catalog.TestJob;
import com.bbh.itss.dso.portal.catalog.TestJobType;
import com.bbh.itss.dso.portal.catalog.TestSettings;
import com.bbh.itss.dso.portal.catalog.TestStage;
import com.bbh.itss.dso.portal.catalog.ToolCommand;
import com.bbh.itss.dso.portal.catalog.UnitTestSettings;
import com.bbh.itss.dso.portal.catalog.UrbanCodeApplicationSettings;
import com.bbh.itss.dso.portal.catalog.UrbanCodeComponent;
import com.bbh.itss.dso.portal.catalog.UrbanCodeSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.pipeline.PipelineRequest;
import com.bbh.itss.dso.portal.pipeline.PipelineResponse;
import com.bbh.itss.dso.portal.pipeline.PipelineService;
import com.bbh.itss.dso.portal.pipeline.PipelineType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
public class DemoDataLoader implements ApplicationRunner {

    static final String DEMO_JENKINS_URL = "https://jenkins.bbh.com";

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String JDK_17 = "/usr/lib/jvm/java-17-openjdk";
    private static final String BITBUCKET = "https://bitbucket.bbh.com/projects/%s/repos/%s";
    private static final String DEPLOY_SCRIPT = "scripts/deployment/zero-downtime-deployment.sh";

    private final ProductRepository products;
    private final ProductCatalogService catalog;
    private final PipelineService pipelines;
    private final ManageGlobalSettingsUseCase settings;

    public DemoDataLoader(ProductRepository products, ProductCatalogService catalog, PipelineService pipelines,
                          ManageGlobalSettingsUseCase settings) {
        this.products = products;
        this.catalog = catalog;
        this.pipelines = pipelines;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (products.count() > 0) {
            return;
        }
        GlobalSettingsValues global = settings.current().values();
        if (global.platform().jenkinsUrl() == null) {
            settings.update(UpdateGlobalSettingsCommand.unversioned(
                    global.withPlatform(global.platform().withJenkinsUrl(DEMO_JENKINS_URL))));
        }

        ProductResponse certScanner = catalog.create(new ProductRequest("CERTSCANNER", "CertScanner",
                "Monitors the validity of TLS certificates across BBH and alerts owners before they expire.",
                "Technology Architecture", "ta-team@bbh.com",
                new AppScanAccount("bbh_b81fbc9f-39c1-8eb4-38b5-b702268969b9", "hcl-app-scan-acount"), null, List.of(
                gradleVm("gui", "Angular front end", "209f44ac-dd06-4ca0-884e-d944904f8020", "cert-scanner-gui",
                        "TA", "cert-scanner", "/opt/ta/CertScanner/gui/deployment", true),
                mavenOpenShift("backend-api", "REST API and certificate scanner", "209f44ac-dd06-4ca0-884e-d944904f8021",
                        "cert-scanner-backend", "TA", "cert-scanner", "ta-certscanner"))));
        ProductResponse payments = catalog.create(new ProductRequest("PAYHUB", "Payments Hub",
                "Payment orchestration platform: gateway, ledger, notifications and reporting.",
                "Payments Engineering", "payments-eng@bbh.com",
                new AppScanAccount("bbh_1c2d3e4f-0000-4abc-9def-123456789abc", null), null, List.of(
                mavenOpenShift("gateway", "Public payment API", "3a1b2c3d-1111-4a5b-8c9d-0e1f2a3b4c5d",
                        "payhub-gateway", "PAY", "payhub-gateway", "pay-payhub"),
                gradleVm("ledger", "Double-entry ledger", "3a1b2c3d-2222-4a5b-8c9d-0e1f2a3b4c5d", "payhub-ledger",
                        "PAY", "payhub-ledger", "/opt/pay/PayHub/ledger/deployment", false),
                gradleVm("notifications", "E-mail and push notifications", "3a1b2c3d-3333-4a5b-8c9d-0e1f2a3b4c5d",
                        "payhub-notifications", "PAY", "payhub-notifications",
                        "/opt/pay/PayHub/notifications/deployment", false),
                flutter("mobile-app", "Flutter mobile application", "3a1b2c3d-4444-4a5b-8c9d-0e1f2a3b4c5d",
                        "PAY", "payhub-mobile"))));

        pipeline(certScanner, "gui", PipelineType.FULL);
        pipeline(certScanner, "gui", PipelineType.SAST);
        pipeline(certScanner, "backend-api", PipelineType.FULL);
        pipeline(payments, "gateway", PipelineType.FULL);
        pipeline(payments, "gateway", PipelineType.SECURITY);
        pipeline(payments, "gateway", PipelineType.EXTENDED);
        pipeline(payments, "ledger", PipelineType.FULL);
        pipeline(payments, "notifications", PipelineType.FULL);
        PipelineResponse retired = pipeline(payments, "mobile-app", PipelineType.SAST);
        pipelines.revokeKey(retired.id(), "Mobile app moved to the new mobile platform pipeline");
        log.info("Created demo data: {} and {}", certScanner.name(), payments.name());
    }

    private static ServiceRequest gradleVm(String name, String description, String appScanId, String sonarKey,
                                           String bitbucketProject, String repo, String deployDir, boolean dast) {
        String title = sonarKey.toUpperCase();
        return new ServiceRequest(null, name, description,
                new BuildSettings(BuildTool.GRADLE, ".", JDK_17, false, "build/libs/*.jar",
                        ToolCommand.of(List.of("clean", "build", "bootJar"), List.of("--refresh-dependencies"))),
                new UnitTestSettings(ToolCommand.of(List.of("test", "jacocoTestReport"), List.of()),
                        "build/test-results/test/*.xml", null, null, false, null),
                new TestSettings(null, 10, 5, null),
                testJobs(title, repo),
                new DeploymentSettings(DeployTarget.VM, null, null, null),
                null,
                UrbanCodeSettings.DEFAULTS,
                List.of(new UrbanCodeApplicationSettings(title, 1, List.of("DV", "RD"), null, List.of(
                        new UrbanCodeComponent(title + "-app", "build/libs", "*.jar", null, null, null, true)))),
                Map.of(Region.RD, new SshTarget(null, null, deployDir, DEPLOY_SCRIPT, null),
                        Region.QC, new SshTarget(null, null, deployDir, DEPLOY_SCRIPT, null)),
                null,
                appScan(appScanId, sonarKey, dast),
                SonarSettings.of(title, sonarKey, ToolCommand.of(List.of("sonarqube"), List.of())),
                NexusIqSettings.of(sonarKey, List.of("**/build/libs/*.jar")),
                ScmSettings.of(BITBUCKET.formatted(bitbucketProject, repo), "bitbucket-http-credentials"),
                GoldenFixPolicyDto.from(GoldenFixPolicy.INHERITED),
                new MetricsSettings(true, null, "test"),
                null);
    }

    private static ServiceRequest mavenOpenShift(String name, String description, String appScanId, String sonarKey,
                                                 String bitbucketProject, String repo, String namespace) {
        String title = sonarKey.toUpperCase();
        String image = "docker-qc.tools.bbh.com/" + namespace + "/" + name;
        return new ServiceRequest(null, name, description,
                new BuildSettings(BuildTool.MAVEN, ".", JDK_17, false, "target/*.jar",
                        new ToolCommand(List.of("clean", "verify"), List.of("-B", "-U"), null, null,
                                List.of("MAVEN_OPTS=-Xms512m -Xmx1g"))),
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
                                sonarKey + "-rd.apps.ocp-rd.testbbh.com", null, null, null, null),
                        Region.QC, new OpenShiftTarget(null, null, null, null, null, null, image, null, null,
                                namespace + "-qc", "openshift/deployment.yaml", "openshift/config-qc.yaml", false,
                                "/actuator/health", sonarKey + "-qc.apps.ocp-qc.testbbh.com", null, null, null, null)),
                appScan(appScanId, sonarKey, true),
                SonarSettings.of(title, sonarKey, ToolCommand.of(List.of("sonar:sonar"), List.of())),
                NexusIqSettings.of(sonarKey, List.of("**/target/*.jar")),
                ScmSettings.of(BITBUCKET.formatted(bitbucketProject, repo), "bitbucket-http-credentials"),
                GoldenFixPolicyDto.from(GoldenFixPolicy.INHERITED),
                new MetricsSettings(true, null, "test"),
                null);
    }

    private static ServiceRequest flutter(String name, String description, String appScanId, String bitbucketProject,
                                          String repo) {
        return new ServiceRequest(null, name, description,
                new BuildSettings(BuildTool.FLUTTER, ".", null, false, null, null),
                null, null, List.of(),
                new DeploymentSettings(DeployTarget.VM, null, null, null),
                null, null, null, null, null,
                AppScanSettings.of(appScanId),
                null,
                null,
                ScmSettings.of(BITBUCKET.formatted(bitbucketProject, repo), "bitbucket-http-credentials"),
                GoldenFixPolicyDto.from(GoldenFixPolicy.inherit(false)),
                new MetricsSettings(true, null, "test"),
                new FlutterSettings(FlutterPlatform.APK, List.of("core", "payments"), List.of("core", "payments"),
                        List.of("core", "payments"), List.of("secure_storage"), "payhub-mobile-signing-password",
                        "payhub-mobile-prod-licence", "payhub-mobile-test-licence", "com.bbh.payhub", "payhub-mobile",
                        "org.apache.maven.plugins:maven-deploy-plugin:3.1.2:deploy-file", "lib", "test", false, null,
                        null));
    }

    private static AppScanSettings appScan(String appScanId, String scanName, boolean dast) {
        return new AppScanSettings(appScanId, scanName + "-sast", List.of(), List.of("node_modules"), true, false,
                false, false, null, null, dast, dast ? scanName + "-dast" : null,
                dast ? "http://rdltaapps1.testbbh.com" : null, null);
    }

    private static List<TestJob> testJobs(String title, String repo) {
        return List.of(
                new TestJob(TestStage.SMOKE, title + " - smoke", TestJobType.LOCAL, repo + "/smoke-tests", 15, null,
                        null, null, null),
                new TestJob(TestStage.SMOKE, title + " - login smoke", null,
                        "https://jenkins-a.bbh.com/job/smoke/job/" + repo + "-login", null, null, null, null,
                        "remote-jenkins-api-token"),
                new TestJob(TestStage.REGRESSION, title + " - regression", TestJobType.LOCAL,
                        repo + "/regression-tests", 60, "ENV=rd", null, null, null),
                new TestJob(TestStage.PERFORMANCE, title + " - performance", TestJobType.LOCAL,
                        repo + "/performance-tests", 120, null, null, null, null));
    }

    private PipelineResponse pipeline(ProductResponse product, String serviceName, PipelineType type) {
        ServiceResponse service = product.services().stream().filter(s -> s.name().equals(serviceName)).findFirst()
                .orElseThrow();
        String folder = "DevSecOps/" + product.code() + "/";
        String extendedJob = type == PipelineType.SECURITY ? folder + serviceName + "-extended" : null;
        String securityJob = type == PipelineType.EXTENDED ? folder + serviceName + "-security" : null;
        return pipelines.create(service.id(), new PipelineRequest(type, List.of("linux-agent"), extendedJob, securityJob,
                folder + serviceName + "-" + type.variant(), null));
    }
}
