package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.catalog.AppScanAccount
import com.bbh.itss.dso.portal.catalog.AppScanSettings
import com.bbh.itss.dso.portal.catalog.BuildSettings
import com.bbh.itss.dso.portal.catalog.BuildTool
import com.bbh.itss.dso.portal.catalog.DeployTarget
import com.bbh.itss.dso.portal.catalog.DeploymentSettings
import com.bbh.itss.dso.portal.catalog.FlutterSettings
import com.bbh.itss.dso.portal.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.catalog.MetricsSettings
import com.bbh.itss.dso.portal.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.catalog.Product
import com.bbh.itss.dso.portal.catalog.ProductDetails
import com.bbh.itss.dso.portal.catalog.ProductRequest
import com.bbh.itss.dso.portal.catalog.Region
import com.bbh.itss.dso.portal.catalog.ScmSettings
import com.bbh.itss.dso.portal.catalog.ServiceDefinition
import com.bbh.itss.dso.portal.catalog.ServiceRequest
import com.bbh.itss.dso.portal.catalog.ServiceSettings
import com.bbh.itss.dso.portal.catalog.SonarSettings
import com.bbh.itss.dso.portal.catalog.SshTarget
import com.bbh.itss.dso.portal.catalog.TestJob
import com.bbh.itss.dso.portal.catalog.TestSettings
import com.bbh.itss.dso.portal.catalog.ToolCommand
import com.bbh.itss.dso.portal.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.catalog.UrbanCodeSettings
import com.bbh.itss.dso.portal.pipeline.Pipeline
import com.bbh.itss.dso.portal.pipeline.PipelineSettings
import com.bbh.itss.dso.portal.pipeline.PipelineType
import com.bbh.itss.dso.portal.settings.GlobalSettingsValues
import org.springframework.test.util.ReflectionTestUtils

/**
 * Valid domain objects for the unit specs. Every builder takes named arguments that override its defaults.
 */
final class Fixtures {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String JDK = '/usr/lib/jvm/java-17-openjdk'

    private Fixtures() {
    }

    /** A command with the given tasks (or goals) and flags. */
    static ToolCommand command(List<String> tasks, List<String> flags = []) {
        ToolCommand.of(tasks, flags)
    }

    static BuildSettings build(Map args = [:]) {
        BuildTool tool = args.tool as BuildTool ?: BuildTool.GRADLE
        ToolCommand defaultCommand = tool == BuildTool.FLUTTER ? ToolCommand.NONE
                : tool == BuildTool.MAVEN ? command(['clean', 'verify']) : command(['clean', 'build'])
        new BuildSettings(tool, args.sourceDir as String,
                args.containsKey('javaPath') ? args.javaPath as String : JDK, args.autoSetup as Boolean,
                args.buildPath as String, args.containsKey('command') ? args.command as ToolCommand : defaultCommand)
    }

    static DeploymentSettings deployment(Map args = [:]) {
        new DeploymentSettings(args.target as DeployTarget ?: DeployTarget.VM, args.appName as String,
                args.artifactName as String, args.baseArtifactName as String)
    }

    static AppScanSettings appScan(Map args = [:]) {
        new AppScanSettings(args.applicationId as String ?: APP_ID, args.sastScanName as String,
                args.includedDirs as List<String>, args.excludedDirs as List<String>, args.compile as Boolean,
                args.sourceCodeOnly as Boolean, args.useConfigFile as Boolean, args.insecureTls as Boolean,
                args.clientPath as String, args.compileCommand as ToolCommand, args.dastEnabled as Boolean,
                args.dastScanName as String, args.dastTargetUrl as String, args.dastPresenceId as String)
    }

    /**
     * Service settings: a Gradle service deployed to a VM with every other section at its default. A Maven
     * service on a VM gets the delivery goals it needs, so the defaults always validate.
     */
    static ServiceSettings settings(Map args = [:]) {
        BuildSettings build = args.build as BuildSettings ?: build()
        DeploymentSettings deployment = args.deployment as DeploymentSettings ?: deployment()
        ToolCommand delivery = args.containsKey('delivery') ? args.delivery as ToolCommand
                : build.tool() == BuildTool.MAVEN && deployment.target() == DeployTarget.VM
                ? command(['deploy:deploy-file']) : null
        new ServiceSettings(build, args.unitTests as UnitTestSettings, args.tests as TestSettings,
                args.testJobs as List<TestJob>, deployment, delivery, args.urbanCode as UrbanCodeSettings,
                args.urbanCodeApplications as List<UrbanCodeApplicationSettings>,
                args.sshTargets as Map<Region, SshTarget>, args.openShiftTargets as Map<Region, OpenShiftTarget>,
                args.appScan as AppScanSettings ?: appScan(), args.sonar as SonarSettings,
                args.nexusIq as NexusIqSettings, args.scm as ScmSettings, args.goldenFix as GoldenFixPolicy,
                args.metrics as MetricsSettings, args.flutter as FlutterSettings)
    }

    static ServiceRequest serviceRequest(Map args = [:]) {
        ServiceSettings s = settings(args)
        new ServiceRequest(args.id as Long, args.name as String ?: 'gui', args.description as String, s.build(),
                s.unitTests(), s.tests(), s.testJobs(), s.deployment(), s.delivery(), s.urbanCode(),
                s.urbanCodeApplications(), s.sshTargets(), s.openShiftTargets(), s.appScan(), s.sonar(), s.nexusIq(),
                s.scm(), s.goldenFix(), s.metrics(), s.flutter())
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
        Pipeline pipeline = new Pipeline(service, type, pipelineSettings(args))
        withId(pipeline, args.id as Long)
    }

    static PipelineSettings pipelineSettings(Map args = [:]) {
        new PipelineSettings(args.agentLabels as List<String> ?: ['linux-agent'], args.extendedPipelineJob as String,
                args.securityPipelineJob as String, args.jenkinsJob as String, args.description as String)
    }

    /** The global settings with BBH's values, changed by the given function. */
    static GlobalSettingsValues globalSettings(Closure<GlobalSettingsValues> change = { it }) {
        change(GlobalSettingsValues.bbhDefaults())
    }

    /** Sets the id JPA would generate. */
    static <T> T withId(T entity, Long id) {
        if (id != null) {
            ReflectionTestUtils.setField(entity, 'id', id)
        }
        entity
    }
}
