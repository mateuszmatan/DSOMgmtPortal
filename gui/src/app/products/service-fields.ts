import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { FlutterPlatform, GlobalSettings, REGIONS, Region } from '../core/models';
import { Field, Fields, GRADLE_MAVEN_FLUTTER, fallback, formRevision } from '../shared/fields';
import { GoldenFixFields } from './golden-fix-fields';
import { OpenShiftTargetFields } from './openshift-target-fields';
import {
  ServiceForm,
  ServiceSectionId,
  firstInvalidSection,
  sectionInvalid,
  sectionTouched,
  visibleSections,
} from './product-form-model';
import { TestJobsFields } from './test-jobs-fields';
import { ToolCommandFields } from './tool-command-fields';
import { UrbanCodeFields } from './urban-code-fields';

const REGION_NAMES: Record<Region, string> = {
  RD: 'RD, the lower test region',
  QC: 'QC, the higher test region',
};

const GENERAL: Field[] = [
  {
    key: 'name',
    label: 'Service name',
    span: 5,
    placeholder: 'backend-api',
    hint: 'Lower case, for example gui or backend-api',
    error: "Use lower case letters, digits, '.', '-' or '_'",
  },
  {
    key: 'description',
    label: 'Description',
    span: 7,
    placeholder: 'REST API and certificate scanner',
  },
];

const UNIT_TESTS: Field[] = [
  { key: 'rootDir', label: 'Root folder', span: 3, mono: true, code: 'tests.unitTests.rootDir' },
  {
    key: 'reportOutDir',
    label: 'Report folder',
    span: 3,
    mono: true,
    code: 'tests.unitTests.reportOutDir',
  },
  {
    key: 'allowEmptyResults',
    kind: 'check',
    label: 'Accept a run without test results',
    code: 'tests.unitTests.allowEmptyResults',
  },
  {
    key: 'coverageReportPath',
    label: 'Coverage report',
    span: 6,
    mono: true,
    placeholder: 'build/reports/jacoco/test/jacocoTestReport.xml',
    code: 'coverage.reportPath',
  },
];

const APP_SCAN: Field[] = [
  {
    key: 'applicationId',
    label: 'AppScan application ID',
    span: 6,
    mono: true,
    placeholder: '109f44ac-cc06-4ca0-884e-d944904f7019',
    code: 'appId',
    error: 'Must be the application UUID from AppScan on Cloud',
  },
  {
    key: 'sastScanName',
    label: 'SAST scan name',
    span: 6,
    code: 'sast.scanName',
    hint: 'left empty: the service name',
  },
];

const APP_SCAN_STATIC: Field[] = [
  {
    key: 'includedDirs',
    kind: 'area',
    label: 'Included folders',
    span: 6,
    mono: true,
    placeholder: 'src/main',
    code: 'includedDirs',
    hint: 'one per line, left empty: all',
  },
  {
    key: 'excludedDirs',
    kind: 'area',
    label: 'Excluded folders',
    span: 6,
    mono: true,
    placeholder: 'src/test',
    code: 'excludedDirs',
    hint: 'one per line',
  },
  {
    key: 'clientPath',
    label: 'AppScan client path',
    span: 6,
    mono: true,
    code: 'appscanPath',
    hint: 'left empty: downloaded by the pipeline',
  },
];

const APP_SCAN_FLAGS: Field[] = [
  {
    key: 'compile',
    kind: 'check',
    span: 6,
    label: 'Compile before preparing the IRX',
    code: 'asoc.doCompile',
  },
  {
    key: 'sourceCodeOnly',
    kind: 'check',
    span: 6,
    label: 'Scan source code only',
    code: 'asoc.sourceCodeOnly',
  },
  {
    key: 'useConfigFile',
    kind: 'check',
    span: 6,
    label: "Use the repository's AppScan configuration",
    code: 'asoc.useAppScanConfig',
  },
  {
    key: 'insecureTls',
    kind: 'check',
    span: 6,
    label: 'Accept any TLS certificate',
    code: 'asoc.insecureTls',
  },
];

const DAST_ENABLED: Field[] = [
  {
    key: 'dastEnabled',
    kind: 'check',
    label: 'Run DAST against the deployed application',
    code: 'dast.enabled',
  },
];

const DAST: Field[] = [
  {
    key: 'dastTargetUrl',
    label: 'DAST target URL',
    span: 6,
    placeholder: 'https://cert-scanner.testbbh.com',
    code: 'dast.targetUrl',
    error: 'Must be an http or https URL',
  },
  { key: 'dastScanName', label: 'DAST scan name', span: 3, code: 'dast.scanName' },
  {
    key: 'dastPresenceId',
    label: 'Presence ID',
    span: 3,
    mono: true,
    code: 'dast.presenceId',
    hint: 'for internal hosts',
  },
];

const NEXUS_IQ: Field[] = [
  {
    key: 'application',
    label: 'Application',
    span: 5,
    mono: true,
    code: 'tools.nexusIq.application',
    hint: 'set with the scan patterns',
  },
  {
    key: 'stage',
    label: 'Stage',
    span: 3,
    mono: true,
    placeholder: 'build',
    code: 'tools.nexusIq.stage',
    error: 'A Nexus IQ stage such as build, stage-release or release',
  },
  { key: 'scaScanName', label: 'SCA scan name', span: 4, code: 'sca.scanName' },
  {
    key: 'scanPatterns',
    kind: 'area',
    label: 'Scan patterns',
    span: 8,
    mono: true,
    placeholder: '**/build/libs/*.jar',
    code: 'tools.nexusIq.scanPatterns',
    hint: 'one Ant pattern per line, set with the application',
  },
  {
    key: 'failOnNetworkError',
    kind: 'check',
    span: 4,
    label: 'Fail the build on a network error',
    code: 'failOnNetworkError',
  },
];

const SCM: Field[] = [
  {
    key: 'repositoryUrl',
    label: 'Repository URL',
    span: 8,
    placeholder: 'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
    code: 'scm.bitbucket.url',
    error: 'Must be an http or https URL',
  },
  {
    key: 'credentialsId',
    label: 'Credentials ID',
    span: 4,
    mono: true,
    placeholder: 'bitbucket-http-credentials',
    code: 'scm.bitbucket.credentialsId',
  },
  {
    key: 'authType',
    kind: 'select',
    label: 'Sign-in',
    span: 4,
    code: 'scm.bitbucket.authType',
    options: [
      { value: 'BASIC', label: 'User name and password or token' },
      { value: 'BEARER', label: 'HTTP access token' },
    ],
  },
  {
    key: 'type',
    kind: 'select',
    label: 'Bitbucket',
    span: 4,
    code: 'scm.bitbucket.type',
    options: [
      { value: null, label: 'Detected from the URL' },
      { value: 'SERVER', label: 'Data Center' },
      { value: 'CLOUD', label: 'Cloud' },
    ],
  },
  {
    key: 'targetBranch',
    label: 'Target branch',
    span: 4,
    mono: true,
    placeholder: 'develop',
    code: 'scm.bitbucket.targetBranch',
  },
  {
    key: 'cloneUrl',
    label: 'Clone URL',
    span: 6,
    placeholder: 'ssh://git@bitbucket.bbh.com/ta/cert.git',
    code: 'scm.bitbucket.cloneUrl',
    hint: 'left empty: the repository URL',
    error: 'Must be an http, https or ssh URL',
  },
  {
    key: 'reviewers',
    label: 'Reviewers',
    span: 6,
    mono: true,
    placeholder: 'jsmith, akowalski',
    code: 'scm.bitbucket.reviewers',
    hint: 'user names, separated by commas',
  },
];

const BITBUCKET_REPOSITORY: Field[] = [
  {
    key: 'apiUrl',
    label: 'Bitbucket API URL',
    span: 6,
    placeholder: 'https://bitbucket.bbh.com',
    code: 'scm.bitbucket.apiUrl',
    hint: 'Data Center base URL or Cloud API',
    error: 'Must be an http or https URL',
  },
  {
    key: 'workspace',
    label: 'Workspace',
    span: 6,
    mono: true,
    placeholder: 'bbh-technology',
    code: 'scm.bitbucket.workspace',
    hint: 'Bitbucket Cloud',
    error: 'No spaces or slashes',
  },
  {
    key: 'projectKey',
    label: 'Project key',
    span: 6,
    mono: true,
    placeholder: 'TA',
    code: 'scm.bitbucket.projectKey',
    hint: 'Data Center',
    error: 'No spaces or slashes',
  },
  {
    key: 'repoSlug',
    label: 'Repository slug',
    span: 6,
    mono: true,
    placeholder: 'cert-scanner',
    code: 'scm.bitbucket.repoSlug',
    hint: 'Cloud and Data Center',
    error: 'No spaces or slashes',
  },
];

const FLUTTER_MODULES: Field[] = [
  {
    key: 'modules',
    kind: 'area',
    label: 'Modules',
    span: 8,
    mono: true,
    code: 'tools.flutter.flutterModules',
    hint: 'one per line, at least one',
  },
  {
    key: 'testModules',
    kind: 'area',
    label: 'Tested modules',
    span: 4,
    mono: true,
    code: 'tests.modules',
    hint: 'at least one',
  },
  {
    key: 'testSubmodules',
    kind: 'area',
    label: 'Tested submodules',
    span: 4,
    mono: true,
    code: 'tests.submodules',
  },
  {
    key: 'testSubplugins',
    kind: 'area',
    label: 'Tested subplugins',
    span: 4,
    mono: true,
    code: 'tests.subplugins',
  },
];

const FLUTTER_CREDENTIALS: Field[] = [
  {
    key: 'signingPasswordCredentialsId',
    label: 'Signing password',
    span: 4,
    mono: true,
    hint: 'Jenkins credentials ID, first entry',
  },
  {
    key: 'prodLicenseCredentialsId',
    label: 'Production licence',
    span: 4,
    mono: true,
    hint: 'Second entry',
  },
  {
    key: 'testLicenseCredentialsId',
    label: 'Test licence',
    span: 4,
    mono: true,
    hint: 'Third entry',
  },
];

const FLUTTER_SONAR: Field[] = [
  {
    key: 'sonarSources',
    label: 'Sources',
    span: 4,
    mono: true,
    placeholder: 'lib',
    code: 'tools.sonar.sources',
  },
  {
    key: 'sonarTests',
    label: 'Tests',
    span: 4,
    mono: true,
    placeholder: 'test',
    code: 'tools.sonar.tests',
  },
  {
    key: 'sonarScannerVersion',
    label: 'SonarScanner version',
    span: 4,
    mono: true,
    placeholder: '5.0.1.3006',
    code: 'tools.sonar.sonarScannerVersion',
    error: 'Must be a version such as 5.0.1.3006',
  },
  {
    key: 'dartAnalyzeCommand',
    label: 'Dart analyze command',
    span: 8,
    mono: true,
    code: 'tools.sonar.dartAnalyzeCommand',
  },
  {
    key: 'sonarFlutterPlugin',
    kind: 'check',
    span: 4,
    label: 'Flutter plugin',
    code: 'tools.sonar.flutterPlugin',
  },
];

const FLUTTER_PLATFORMS: FlutterPlatform[] = [
  'APK',
  'APPBUNDLE',
  'IOS',
  'MACOS',
  'LINUX',
  'WINDOWS',
  'WEB',
];

@Component({
  selector: 'dso-service-fields',
  imports: [
    ReactiveFormsModule,
    MatButtonToggleModule,
    MatIconModule,
    Fields,
    GoldenFixFields,
    OpenShiftTargetFields,
    TestJobsFields,
    ToolCommandFields,
    UrbanCodeFields,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './service-fields.html',
  styleUrl: './service-fields.scss',
})
export class ServiceFields {
  readonly form = input.required<ServiceForm>();
  readonly productCode = input('');
  readonly defaults = input<GlobalSettings | null>(null);
  readonly submitted = input(false);

  private readonly selected = signal<ServiceSectionId>('general');
  private readonly changes = formRevision(this.form);

  protected readonly regions = REGIONS;
  protected readonly regionNames = REGION_NAMES;
  protected readonly general = GENERAL;
  protected readonly appScan = APP_SCAN;
  protected readonly appScanStatic = APP_SCAN_STATIC;
  protected readonly appScanFlags = APP_SCAN_FLAGS;
  protected readonly dastEnabled = DAST_ENABLED;
  protected readonly dast = DAST;
  protected readonly nexusIq = NEXUS_IQ;
  protected readonly scm = SCM;
  protected readonly bitbucketRepository = BITBUCKET_REPOSITORY;
  protected readonly flutterModules = FLUTTER_MODULES;
  protected readonly flutterCredentials = FLUTTER_CREDENTIALS;
  protected readonly flutterSonar = FLUTTER_SONAR;

  protected readonly sections = computed(() => {
    this.changes();
    const form = this.form();
    return visibleSections(form).map((section) => ({
      ...section,
      problem: sectionInvalid(form, section) && (this.submitted() || sectionTouched(form, section)),
    }));
  });

  protected current(): ServiceSectionId {
    const id = this.selected();
    return visibleSections(this.form()).some((section) => section.id === id) ? id : 'general';
  }

  protected select(id: ServiceSectionId): void {
    this.selected.set(id);
  }

  revealFirstProblem(): boolean {
    const id = firstInvalidSection(this.form());
    if (id) {
      this.selected.set(id);
    }
    return id !== null;
  }

  protected tool() {
    return this.form().controls.build.controls.tool.value;
  }

  protected isVm(): boolean {
    return this.form().controls.deployment.controls.target.value === 'VM';
  }

  protected flutter(): boolean {
    return this.tool() === 'FLUTTER';
  }

  protected buildFields(): Field[] {
    const maven = this.tool() === 'MAVEN';
    return [
      {
        key: 'tool',
        kind: 'select',
        label: 'Build tool',
        span: 3,
        code: 'buildTool',
        options: GRADLE_MAVEN_FLUTTER,
      },
      {
        key: 'sourceDir',
        label: 'Source folder',
        span: 3,
        mono: true,
        placeholder: '.',
        code: 'sourceDir',
        hint: 'relative to the repository root',
      },
      {
        key: 'javaPath',
        label: 'JDK path',
        span: 6,
        mono: true,
        placeholder: '/usr/lib/jvm/java-17-openjdk',
        code: 'javaPath',
        hint: this.flutter()
          ? 'JAVA_HOME of the Flutter build stages'
          : 'JAVA_HOME of the unit tests stage, unless the build tool is set up automatically',
      },
      {
        key: 'autoSetup',
        kind: 'check',
        span: 6,
        label: 'Set up the build tool automatically',
        code: 'buildToolAutoSetup',
      },
      {
        key: 'buildPath',
        label: 'Artifact path',
        span: 6,
        mono: true,
        placeholder: maven ? 'target/*.jar' : 'build/libs/*.jar',
        code: 'build.buildPath',
        hint:
          this.isVm() && maven
            ? 'the artifact the Nexus snapshot delivery uploads'
            : 'what the build produces',
      },
    ];
  }

  protected unitTestFields(): Field[] {
    return [
      {
        key: 'resultPattern',
        label: 'Test results',
        span: 6,
        mono: true,
        placeholder:
          this.tool() === 'MAVEN'
            ? 'target/surefire-reports/*.xml'
            : 'build/test-results/test/*.xml',
        code: 'tests.unitTests.unitTestResult',
        hint: 'JUnit XML files',
      },
      ...UNIT_TESTS,
    ];
  }

  protected deploymentFields(): Field[] {
    const openShift = this.isVm() ? '' : 'required for OpenShift';
    return [
      {
        key: 'appName',
        label: 'Application name',
        span: 4,
        placeholder: 'cert-scanner-api',
        code: 'appName',
        hint: openShift,
      },
      {
        key: 'artifactName',
        label: 'Artifact name',
        span: 4,
        placeholder: 'cert-scanner-api.jar',
        code: 'artifactName',
        hint: openShift,
      },
      {
        key: 'baseArtifactName',
        label: 'Built file name',
        span: 4,
        placeholder: 'app-1.0.0.jar',
        code: 'baseArtifactName',
        hint: 'renamed to the artifact name',
      },
    ];
  }

  protected sshFields(region: Region): Field[] {
    const deployment = this.defaults()?.deployment;
    const host = region === 'RD' ? deployment?.rdHost : deployment?.qcHost;
    return [
      {
        key: 'host',
        label: 'Host',
        span: 4,
        mono: true,
        placeholder: host ?? '',
        code: 'host',
        hint: fallback(host),
        error: 'Must be a host name such as rdltaapps1.testbbh.com',
      },
      {
        key: 'user',
        label: 'User',
        span: 3,
        mono: true,
        placeholder: deployment?.sshUser ?? '',
        code: 'user',
        hint: fallback(deployment?.sshUser),
      },
      {
        key: 'deployDir',
        label: 'Deployment folder',
        span: 5,
        mono: true,
        placeholder: '/opt/ta/CertScanner/gui/deployment',
        code: 'deployDir',
      },
      {
        key: 'deployScript',
        label: 'Deployment script',
        span: 6,
        mono: true,
        placeholder: deployment?.deployScript ?? '',
        code: 'deployScript',
        hint: fallback(deployment?.deployScript),
      },
      {
        key: 'versionFile',
        label: 'Version file',
        span: 6,
        mono: true,
        placeholder: deployment?.versionFile ?? '',
        code: 'versionFile',
        hint: fallback(deployment?.versionFile),
      },
    ];
  }

  protected sonarFields(): Field[] {
    const installation = this.defaults()?.platform?.sonarInstallationName;
    return [
      { key: 'projectName', label: 'Project name', span: 6, code: 'tools.sonar.projectName' },
      {
        key: 'projectKey',
        label: 'Project key',
        span: 6,
        mono: true,
        code: 'tools.sonar.projectKey',
        hint: 'unique across BBH',
        error: "Letters, digits, '-', '_', '.' and ':' with at least one non-digit",
      },
      {
        key: 'installationName',
        label: 'Jenkins installation',
        span: 4,
        placeholder: installation ?? '',
        code: 'tools.sonar.installationName',
        hint: fallback(installation),
      },
      {
        key: 'credentialsId',
        label: 'Credentials ID',
        span: 4,
        mono: true,
        code: 'tools.sonar.credentialsId',
      },
      {
        key: 'authTokenCredentialsId',
        label: 'Token credentials ID',
        span: 4,
        mono: true,
        code: 'tools.sonar.authToken',
      },
      {
        key: 'badgeToken',
        label: 'Badge token',
        span: 4,
        mono: true,
        placeholder: 'sqb_1a2b3c',
        code: 'tools.sonar.badgeToken',
        error: 'Must be a SonarQube badge token',
      },
      {
        key: 'addBadges',
        kind: 'check',
        span: 4,
        label: 'Badges in the report',
        code: 'addBadges',
      },
      { key: 'fullBadges', kind: 'check', span: 4, label: 'Every badge', code: 'fullBadges' },
    ];
  }

  protected goldenFixField(): Field[] {
    const enabled = this.defaults()?.goldenFix.enabled;
    return [
      {
        key: 'enabled',
        kind: 'select',
        label: 'Run GoldenFix',
        span: 4,
        code: 'goldenFix.enabled',
        hint: enabled === undefined ? '' : `Global default: ${enabled ? 'on' : 'off'}`,
        options: [
          { value: null, label: 'Global default' },
          { value: true, label: 'On' },
          { value: false, label: 'Off' },
        ],
      },
    ];
  }

  protected metricsFields(): Field[] {
    const project = `${this.productCode() || 'CODE'}-${this.form().controls.name.value || 'service'}`;
    return [
      {
        key: 'enabled',
        kind: 'check',
        label: 'Write pipeline metrics to InfluxDB',
        code: 'influx.enabled',
      },
      {
        key: 'influxProject',
        label: 'Project tag',
        span: 8,
        mono: true,
        placeholder: project,
        code: 'influx.project',
        hint: `left empty: ${project}`,
        error: "Letters, digits, '.', '-' and '_'",
      },
      {
        key: 'influxEnv',
        label: 'Environment tag',
        span: 4,
        mono: true,
        code: 'influx.env',
        error: "Letters, digits, '.', '-' and '_'",
      },
    ];
  }

  protected flutterPlatformField(): Field[] {
    return [
      {
        key: 'platform',
        kind: 'select',
        label: 'Platform',
        span: 4,
        code: 'flutter.platform',
        options: [
          { value: null, label: 'Library default' },
          ...FLUTTER_PLATFORMS.map((platform) => ({
            value: platform,
            label: platform.toLowerCase(),
          })),
        ],
      },
    ];
  }

  protected flutterDelivery(): Field[] {
    return [
      {
        key: 'deliveryGroup',
        label: 'Group',
        span: 4,
        mono: true,
        placeholder: 'com.bbh.payhub',
        code: 'delivery.group',
        hint: this.isVm() ? 'required on virtual machines' : '',
      },
      {
        key: 'deliveryArtifact',
        label: 'Artifact',
        span: 4,
        mono: true,
        code: 'delivery.artifact',
      },
      {
        key: 'deliveryPlugin',
        label: 'Maven plugin',
        span: 4,
        mono: true,
        code: 'delivery.plugin',
      },
    ];
  }

  protected inheritedGoldenFix(): string {
    const intro = 'The service follows the GoldenFix defaults of the DevSecOps Global Settings';
    const g = this.defaults()?.goldenFix;
    if (!g) {
      return `${intro}.`;
    }
    const dependencies = g.onlyDirectDependencies
      ? 'direct dependencies only'
      : 'direct and transitive dependencies';
    const verification = g.verifyEnabled
      ? 'each fix verified by a build'
      : 'without a verification build';
    return (
      `${intro}: ${g.ecosystems.join(', ')}, threat level ${g.minThreatLevel} and above, ` +
      `${dependencies}, ${verification}.`
    );
  }
}
