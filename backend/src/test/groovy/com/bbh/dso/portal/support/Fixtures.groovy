package com.bbh.dso.portal.support

import com.bbh.dso.portal.catalog.AdditionalConfig
import com.bbh.dso.portal.catalog.AppScanAccount
import com.bbh.dso.portal.catalog.AppScanSettings
import com.bbh.dso.portal.catalog.BuildSettings
import com.bbh.dso.portal.catalog.BuildTool
import com.bbh.dso.portal.catalog.DeployTarget
import com.bbh.dso.portal.catalog.DeploymentSettings
import com.bbh.dso.portal.catalog.MetricsSettings
import com.bbh.dso.portal.catalog.NexusIqSettings
import com.bbh.dso.portal.catalog.Product
import com.bbh.dso.portal.catalog.ProductDetails
import com.bbh.dso.portal.catalog.ProductRequest
import com.bbh.dso.portal.catalog.ScmSettings
import com.bbh.dso.portal.catalog.ServiceDefinition
import com.bbh.dso.portal.catalog.ServiceRequest
import com.bbh.dso.portal.catalog.ServiceSettings
import com.bbh.dso.portal.catalog.SonarSettings
import com.bbh.dso.portal.pipeline.Pipeline
import com.bbh.dso.portal.pipeline.PipelineSettings
import com.bbh.dso.portal.pipeline.PipelineType
import org.springframework.test.util.ReflectionTestUtils

/**
 * Valid domain objects for the unit specs. Every builder takes named arguments that override its defaults.
 */
final class Fixtures {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String JDK = '/usr/lib/jvm/java-17-openjdk'

    private Fixtures() {
    }

    static BuildSettings build(Map args = [:]) {
        new BuildSettings(args.tool ?: BuildTool.GRADLE, args.sourceDir as String,
                args.containsKey('javaPath') ? args.javaPath as String : JDK, args.autoSetup as Boolean)
    }

    static DeploymentSettings deployment(Map args = [:]) {
        new DeploymentSettings(args.target ?: DeployTarget.VM, args.appName as String, args.artifactName as String)
    }

    static AppScanSettings appScan(Map args = [:]) {
        new AppScanSettings(args.applicationId as String ?: APP_ID, args.sastScanName as String,
                args.dastEnabled as Boolean, args.dastTargetUrl as String, args.dastPresenceId as String)
    }

    static ServiceSettings settings(Map args = [:]) {
        new ServiceSettings(args.build as BuildSettings ?: build(), args.deployment as DeploymentSettings ?: deployment(),
                args.appScan as AppScanSettings ?: appScan(), args.sonar as SonarSettings,
                args.nexusIq as NexusIqSettings, args.scm as ScmSettings, args.metrics as MetricsSettings,
                args.additionalConfig as AdditionalConfig)
    }

    static ServiceRequest serviceRequest(Map args = [:]) {
        ServiceSettings s = settings(args)
        new ServiceRequest(args.id as Long, args.name as String ?: 'gui', args.description as String, s.build(),
                s.deployment(), s.appScan(), s.sonar(), s.nexusIq(), s.scm(), s.metrics(), s.additionalConfig())
    }

    static ProductRequest productRequest(Map args = [:]) {
        new ProductRequest(args.code as String ?: 'CERT', args.name as String ?: 'CertScanner',
                args.description as String, args.ownerTeam as String, args.contactEmail as String,
                args.appScan as AppScanAccount ?: account(), args.version as Long,
                args.services as List<ServiceRequest> ?: [serviceRequest()])
    }

    static AppScanAccount account() {
        new AppScanAccount('bbh_key-id', 'hcl-app-scan-account')
    }

    static Product product(Map args = [:]) {
        Product product = new Product(new ProductDetails(args.code as String ?: 'CERT', args.name as String ?: 'CertScanner',
                args.description as String, args.ownerTeam as String, args.contactEmail as String), account())
        withId(product, args.id as Long)
    }

    static ServiceDefinition service(Product product) {
        service([:], product)
    }

    /** Adds a service to the product, with the id JPA would assign when {@code id} is given. */
    static ServiceDefinition service(Map args, Product product) {
        ServiceDefinition service = product.addService(args.name as String ?: 'gui', args.description as String,
                product.services.size(), settings(args))
        withId(service, args.id as Long)
    }

    static Pipeline pipeline(ServiceDefinition service) {
        pipeline([:], service)
    }

    static Pipeline pipeline(Map args, ServiceDefinition service) {
        PipelineType type = args.type as PipelineType ?: PipelineType.FULL
        Pipeline pipeline = new Pipeline(service, type,
                new PipelineSettings(args.agentLabels as List<String> ?: ['linux-agent'], args.extendedPipelineJob as String,
                        args.description as String))
        withId(pipeline, args.id as Long)
    }

    /** Sets the id JPA would generate. */
    static <T> T withId(T entity, Long id) {
        if (id != null) {
            ReflectionTestUtils.setField(entity, 'id', id)
        }
        entity
    }
}
