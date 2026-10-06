package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount
import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings
import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType
import com.bbh.itss.dso.portal.domain.catalog.BuildSettings
import com.bbh.itss.dso.portal.domain.catalog.BuildTool
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget
import com.bbh.itss.dso.portal.domain.catalog.DeploymentSettings
import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings
import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy
import com.bbh.itss.dso.portal.domain.catalog.MetricsSettings
import com.bbh.itss.dso.portal.domain.catalog.NexusIqApplication
import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings
import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails
import com.bbh.itss.dso.portal.domain.catalog.ProductDirectory
import com.bbh.itss.dso.portal.domain.catalog.Region
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings
import com.bbh.itss.dso.portal.domain.catalog.Service
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings
import com.bbh.itss.dso.portal.domain.catalog.SonarSettings
import com.bbh.itss.dso.portal.domain.catalog.SshTarget
import com.bbh.itss.dso.portal.domain.catalog.TestJob
import com.bbh.itss.dso.portal.domain.catalog.TestJobType
import com.bbh.itss.dso.portal.domain.catalog.TestSettings
import com.bbh.itss.dso.portal.domain.catalog.TestStage
import com.bbh.itss.dso.portal.domain.catalog.ToolCommand
import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings
import com.bbh.itss.dso.portal.domain.pipeline.KeyStatus
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline
import com.bbh.itss.dso.portal.domain.pipeline.PipelineKey
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import com.bbh.itss.dso.portal.domain.pipeline.ServiceRef
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues

import java.time.Instant

final class Fixtures {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String JDK = '/usr/lib/jvm/java-17-openjdk'
    static final String KEY = '0f8fad5b-d9cb-469f-a165-70867728950e'
    static final Instant CREATED = Instant.parse('2026-10-01T08:00:00Z')
    static final Instant UPDATED = Instant.parse('2026-10-02T09:30:00Z')

    private Fixtures() {
    }

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
                args.dastScanName as String, args.dastTargetUrl as String, args.dastPresenceId as String,
                args.secretCredentialsId as String)
    }

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
                args.nexusIq as NexusIqSettings, args.nexusIqApplications as List<NexusIqApplication>,
                args.scm as ScmSettings, args.goldenFix as GoldenFixPolicy,
                args.metrics as MetricsSettings, args.flutter as FlutterSettings)
    }

    static ServiceSettings fullSettings(String name = 'gui') {
        new ServiceSettings(
                new BuildSettings(BuildTool.MAVEN, 'app', JDK, true, 'target/*.war', fullCommand('build')),
                new UnitTestSettings(fullCommand('test'), '**/TEST-*.xml', 'app', 'reports', true, 'target/jacoco.xml'),
                new TestSettings(5, 1, 2, 3, true, false, true, 10, null, 30),
                [new TestJob(TestStage.SMOKE, 'smoke', TestJobType.LOCAL, "CERT/${name}-smoke", 10, 'A=1', null, null, null,
                        null, null, false, false, false, false, false, false),
                 new TestJob(TestStage.REGRESSION, 'regression', TestJobType.REMOTE, 'CERT/regression', 60, 'B=2\nC=3',
                         'jenkins-qc', 'https://jenkins-qc.bbh.com', 'remote-token', 20, 'remote-trigger-token', true,
                         false, true, false, true, false),
                 new TestJob(TestStage.PERFORMANCE, null, null, 'https://jenkins-qc.bbh.com/job/load/', null, null, null,
                         null, null, null, null, false, false, false, false, false, false)],
                new DeploymentSettings(DeployTarget.VM, "cert-${name}", "cert-${name}.war", 'cert-base'),
                fullCommand('delivery'),
                new UrbanCodeSettings('BBH-RD', 'Deploy', true, false, true, false, true, 'release', 'a=b'),
                [new UrbanCodeApplicationSettings('Cert', 1, ['RD', 'QC'], 'snap', 'BBH-QC', 'Deploy QC', false, null,
                        true, null, false, 'cert release', 'cert', 'c=d',
                        [new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', '*.tmp', 'v', '1.0', true, 'war',
                                'UTF-8', 'gui push', 'build=1', 'gui version'),
                         new UrbanCodeComponent('cert-config', 'config', '*.yml', null, null, null, false, null, null,
                                 null, null, null)]),
                 UrbanCodeApplicationSettings.of('Cert Batch', 2, [], null,
                         [UrbanCodeComponent.of('cert-batch', 'batch/build', '*.jar')])],
                [(Region.RD): new SshTarget('rd.host', 'dsoadm', '/opt/rd', 'deploy.sh', 'version.txt'),
                 (Region.QC): new SshTarget('qc.host', null, null, null, null)],
                [(Region.RD): new OpenShiftTarget('cert-build', 'oc/build.yaml', 'Dockerfile', '.', 'add.txt',
                        'push.bbh.com/cert', 'pull.bbh.com/cert', 'certs', 'auth.json', 'cert-rd', 'oc/deploy.yaml',
                        'oc/config', true, '/health', 'cert.apps.bbh.com', 'deploy', 'https://bitbucket.bbh.com/scm/ta/deploy.git',
                        'main', 'deploy-creds', '1.0.42', 'image-registry.openshift-image-registry.svc:5000/cert'),
                 (Region.QC): new OpenShiftTarget(null, null, null, null, null, null, null, null, null, 'cert-qc', null,
                         null, false, null, null, null, null, null, null, '1.0.41', null)],
                new AppScanSettings(APP_ID, 'Cert scan', ['src'], ['test'], false, true, true, true, '/opt/appscan',
                        fullCommand('compile'), true, 'Cert DAST', 'https://cert.testbbh.com', 'presence-1',
                        'cert-appscan-secret'),
                new SonarSettings('CertScanner', "cert-${name}", 'SonarQube BBH', 'sonar-creds', 'sonar-token', 'badge',
                        true, true, fullCommand('sonar'), 'https://sonar.cert.bbh.com'),
                new NexusIqSettings('https://iq.cert.bbh.com', 'cert-iq', 'Cert SCA'),
                [new NexusIqApplication("cert-${name}", ['**/*.war', '**/*.jar'], 'release', true),
                 NexusIqApplication.of("cert-${name}-batch", ['**/batch/*.jar'])],
                new ScmSettings('https://bitbucket.bbh.com/scm/ta/cert.git', 'bb-creds', BitbucketAuthType.BEARER,
                        BitbucketType.SERVER, 'develop', 'ssh://git@bitbucket.bbh.com/ta/cert.git', ['alice', 'bob'],
                        'https://bitbucket.bbh.com/rest/api/1.0', 'ta-workspace', 'TA', 'cert-' + name),
                new GoldenFixPolicy(false, true, 7, ['maven', 'npm'], ['recommended-non-breaking'], ['docs', 'tests'],
                        true, 3, 30, 'mvn verify', 'gradle check', 'npm test', 'pytest', 'flutter test', 'GoldenFix Bot',
                        'goldenfix@bbh.com', 'Europe/Warsaw'),
                new MetricsSettings(false, "cert-${name}", 'qc', 'https://influx.cert.bbh.com/api/v2/write', 'cert-influx'),
                new FlutterSettings(FlutterPlatform.APPBUNDLE, ['core', 'app'], ['core'], ['core/sub'], ['plugin'],
                        'signing', 'prod-licence', 'test-licence', 'com.bbh', 'cert-mobile', 'deploy:deploy-file', 'lib',
                        'test', true, 'dart analyze', '5.0'))
    }

    static ToolCommand fullCommand(String prefix) {
        new ToolCommand([prefix + '-task', 'second'], ['--' + prefix], prefix, '/opt/' + prefix + '/maven',
                [prefix.toUpperCase() + '_OPTS=-Xmx1g', 'CI=true'], prefix.capitalize() + ' step', prefix == 'build')
    }

    static AppScanAccount account() {
        new AppScanAccount('bbh_key-id', 'hcl-app-scan-account')
    }

    static ProductDetails details(Map args = [:]) {
        new ProductDetails(args.code as String ?: 'CERT', args.name as String ?: 'CertScanner',
                args.description as String, args.ownerTeam as String, args.contactEmail as String)
    }

    static ServiceDraft draft(Map args = [:]) {
        new ServiceDraft(args.id as Long, args.name as String ?: 'gui', args.description as String,
                args.settings as ServiceSettings ?: settings(args))
    }

    static Service service(Map args = [:]) {
        new Service(args.id as Long, args.name as String ?: 'gui', args.description as String,
                (args.displayOrder ?: 0) as int, args.settings as ServiceSettings ?: settings(args))
    }

    static Product product(Map args = [:]) {
        ProductDetails details = details(args)
        List<Map> services = args.containsKey('services') ? args.services as List<Map> : []
        List<Service> stored = services.withIndex().collect { Map entry, int order ->
            Service plain = service(entry + [displayOrder: entry.displayOrder ?: order])
            new Service(plain.id(), plain.name(), plain.description(), plain.displayOrder(),
                    plain.settings().withDefaultMetricsProject(details.code(), plain.name()))
        }
        Product.restore(args.containsKey('id') ? args.id as Long : 1L, details, args.appScan as AppScanAccount ?: account(),
                stored, (args.version ?: 0) as long, CREATED, UPDATED)
    }

    static PipelineSettings pipelineSettings(Map args = [:]) {
        new PipelineSettings(args.agentLabels as List<String> ?: ['linux-agent'], args.extendedPipelineJob as String,
                args.securityPipelineJob as String, args.jenkinsJob as String, args.description as String)
    }

    static PipelineKey activeKey(Map args = [:]) {
        new PipelineKey((args.id ?: 100L) as Long, args.value as String ?: KEY, KeyStatus.ACTIVE,
                args.issuedAt as Instant ?: CREATED, null, null, args.lastUsedAt as Instant)
    }

    static PipelineKey revokedKey(Map args = [:]) {
        new PipelineKey((args.id ?: 99L) as Long, args.value as String ?: '6ba7b810-9dad-41d1-80b4-00c04fd430c8',
                KeyStatus.REVOKED, args.issuedAt as Instant ?: CREATED, args.revokedAt as Instant ?: UPDATED,
                args.containsKey('reason') ? args.reason as String : 'Replaced by a new key', null)
    }

    static Pipeline pipeline(Map args = [:]) {
        Pipeline.restore(args.containsKey('id') ? args.id as Long : 20L,
                new ServiceRef((args.productId ?: 1L) as long, (args.serviceId ?: 10L) as long),
                args.type as PipelineType ?: PipelineType.FULL, pipelineSettings(args),
                args.containsKey('keys') ? args.keys as List<PipelineKey> : [activeKey()], (args.version ?: 0) as long,
                CREATED, UPDATED)
    }

    static ProductDirectory directory(Map args = [:]) {
        new ProductDirectory() {
            @Override
            Optional<ProductDirectory.ProductIdentity> findProductByCode(String code) {
                Optional.ofNullable((args.byCode as Map)?.get(code) as ProductDirectory.ProductIdentity)
            }

            @Override
            Optional<ProductDirectory.ProductIdentity> findProductByName(String name) {
                Optional.ofNullable((args.byName as Map)?.get(name) as ProductDirectory.ProductIdentity)
            }
        }
    }

    static GlobalSettingsValues globalSettings(Closure<GlobalSettingsValues> change = { it }) {
        change(GlobalSettingsValues.bbhDefaults())
    }

    static GlobalSettings storedSettings(Closure<GlobalSettingsValues> change = { it }) {
        new GlobalSettings(globalSettings(change), 1, Instant.parse('2026-10-04T12:00:00Z'))
    }

    static GlobalSettings storedSettings(String jenkinsUrl) {
        storedSettings { it.withPlatform(it.platform().withJenkinsUrl(jenkinsUrl)) }
    }

    static <T extends Record> T copy(Map changes, T record) {
        def components = record.class.recordComponents
        def args = components.collect { changes.containsKey(it.name) ? changes[it.name] : it.accessor.invoke(record) }
        record.class.declaredConstructors.find { it.parameterCount == components.length }
                .newInstance(args as Object[]) as T
    }
}
