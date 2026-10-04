package com.bbh.itss.dso.portal.demo;

import com.bbh.itss.dso.portal.catalog.AdditionalConfig;
import com.bbh.itss.dso.portal.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.catalog.BuildSettings;
import com.bbh.itss.dso.portal.catalog.BuildTool;
import com.bbh.itss.dso.portal.catalog.DeployTarget;
import com.bbh.itss.dso.portal.catalog.DeploymentSettings;
import com.bbh.itss.dso.portal.catalog.MetricsSettings;
import com.bbh.itss.dso.portal.catalog.NexusIqSettings;
import com.bbh.itss.dso.portal.catalog.ProductCatalogService;
import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.catalog.ProductRequest;
import com.bbh.itss.dso.portal.catalog.ProductResponse;
import com.bbh.itss.dso.portal.catalog.ScmSettings;
import com.bbh.itss.dso.portal.catalog.ServiceRequest;
import com.bbh.itss.dso.portal.catalog.ServiceResponse;
import com.bbh.itss.dso.portal.catalog.SonarSettings;
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

/**
 * Fills an empty database with two sample products so a local start shows every page with content.
 * Enabled by {@code dso.demo-data=true}, which the local and mysql profiles set.
 */
@Component
@ConditionalOnBooleanProperty("dso.demo-data")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String JDK_17 = "/usr/lib/jvm/java-17-openjdk";
    private static final String BITBUCKET = "https://bitbucket.bbh.com/projects/%s/repos/%s";

    private final ProductRepository products;
    private final ProductCatalogService catalog;
    private final PipelineService pipelines;

    public DemoDataLoader(ProductRepository products, ProductCatalogService catalog, PipelineService pipelines) {
        this.products = products;
        this.catalog = catalog;
        this.pipelines = pipelines;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (products.count() > 0) {
            return;
        }
        ProductResponse certScanner = catalog.create(new ProductRequest("CERTSCANNER", "CertScanner",
                "Monitors the validity of TLS certificates across BBH and alerts owners before they expire.",
                "Technology Architecture", "ta-team@bbh.com",
                new AppScanAccount("bbh_b81fbc9f-39c1-8eb4-38b5-b702268969b9", "hcl-app-scan-acount"), null, List.of(
                service("gui", "Angular front end", BuildTool.GRADLE, DeployTarget.VM,
                        "209f44ac-dd06-4ca0-884e-d944904f8020", "cert-scanner-gui", "TA", "cert-scanner", true,
                        """
                        tests:
                          smoke:
                            jobs:
                              - name: CertScanner-GUI - smoke
                                type: local
                                job: cert-scanner/smoke-tests
                                timeoutMin: 15
                        """),
                service("backend-api", "REST API and certificate scanner", BuildTool.MAVEN, DeployTarget.OPENSHIFT,
                        "209f44ac-dd06-4ca0-884e-d944904f8021", "cert-scanner-backend", "TA", "cert-scanner", false,
                        null))));
        ProductResponse payments = catalog.create(new ProductRequest("PAYHUB", "Payments Hub",
                "Payment orchestration platform: gateway, ledger, notifications and reporting.",
                "Payments Engineering", "payments-eng@bbh.com",
                new AppScanAccount("bbh_1c2d3e4f-0000-4abc-9def-123456789abc", null), null, List.of(
                service("gateway", "Public payment API", BuildTool.MAVEN, DeployTarget.OPENSHIFT,
                        "3a1b2c3d-1111-4a5b-8c9d-0e1f2a3b4c5d", "payhub-gateway", "PAY", "payhub-gateway", true, null),
                service("ledger", "Double-entry ledger", BuildTool.GRADLE, DeployTarget.VM,
                        "3a1b2c3d-2222-4a5b-8c9d-0e1f2a3b4c5d", "payhub-ledger", "PAY", "payhub-ledger", false, null),
                service("notifications", "E-mail and push notifications", BuildTool.GRADLE, DeployTarget.VM,
                        "3a1b2c3d-3333-4a5b-8c9d-0e1f2a3b4c5d", "payhub-notifications", "PAY", "payhub-notifications",
                        false, null),
                service("mobile-app", "Flutter mobile application", BuildTool.FLUTTER, DeployTarget.VM,
                        "3a1b2c3d-4444-4a5b-8c9d-0e1f2a3b4c5d", null, "PAY", "payhub-mobile", false,
                        "flutter:\n  platform: apk\n"))));

        pipeline(certScanner, "gui", PipelineType.FULL);
        pipeline(certScanner, "gui", PipelineType.SAST);
        pipeline(certScanner, "backend-api", PipelineType.FULL);
        pipeline(payments, "gateway", PipelineType.FULL);
        pipeline(payments, "gateway", PipelineType.SECURITY);
        pipeline(payments, "ledger", PipelineType.FULL);
        pipeline(payments, "notifications", PipelineType.FULL);
        PipelineResponse retired = pipeline(payments, "mobile-app", PipelineType.SAST);
        pipelines.revokeKey(retired.id(), "Mobile app moved to the new mobile platform pipeline");
        log.info("Created demo data: {} and {}", certScanner.name(), payments.name());
    }

    private static ServiceRequest service(String name, String description, BuildTool tool, DeployTarget target,
                                          String appScanId, String sonarKey, String bitbucketProject, String repo,
                                          boolean dast, String additionalYaml) {
        boolean openShift = target == DeployTarget.OPENSHIFT;
        return new ServiceRequest(null, name, description,
                new BuildSettings(tool, ".", tool == BuildTool.FLUTTER ? null : JDK_17, false),
                new DeploymentSettings(target, openShift ? name : null, openShift ? name + ".jar" : null),
                new AppScanSettings(appScanId, null, dast, dast ? "http://rdltaapps1.testbbh.com" : null, null),
                new SonarSettings(sonarKey, sonarKey),
                new NexusIqSettings(sonarKey, List.of("**/build/libs/*.jar")),
                new ScmSettings(BITBUCKET.formatted(bitbucketProject, repo), "bitbucket-http-credentials", true),
                new MetricsSettings(true, null, "test"),
                new AdditionalConfig(additionalYaml));
    }

    private PipelineResponse pipeline(ProductResponse product, String serviceName, PipelineType type) {
        ServiceResponse service = product.services().stream().filter(s -> s.name().equals(serviceName)).findFirst()
                .orElseThrow();
        String extendedJob = type == PipelineType.SECURITY ? product.code() + "/" + serviceName + "-extended" : null;
        return pipelines.create(service.id(), new PipelineRequest(type, List.of("linux-agent"), extendedJob, null));
    }
}
