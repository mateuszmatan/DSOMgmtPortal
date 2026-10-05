export type BuildTool = 'GRADLE' | 'MAVEN' | 'FLUTTER';
export type DeployTarget = 'VM' | 'OPENSHIFT';
export type PipelineType = 'FULL' | 'SECURITY' | 'EXTENDED' | 'SAST';
export type KeyStatus = 'ACTIVE' | 'REVOKED';
export type RunResult =
  'SUCCESS' | 'UNSTABLE' | 'FAILURE' | 'ABORTED' | 'NOT_BUILT' | 'NO_DATA' | 'DISABLED';
export type DoraLevel = 'ELITE' | 'HIGH' | 'MEDIUM' | 'LOW';
export type TestStage = 'SMOKE' | 'REGRESSION' | 'PERFORMANCE';
export type TestJobType = 'LOCAL' | 'REMOTE';
export type Region = 'RD' | 'QC';
export type BitbucketAuthType = 'BASIC' | 'BEARER';
export type BitbucketType = 'SERVER' | 'CLOUD';
export type FlutterPlatform = 'APK' | 'APPBUNDLE' | 'IOS' | 'MACOS' | 'LINUX' | 'WINDOWS' | 'WEB';
export type Scanner = 'SAST' | 'SCA' | 'NEXUS_IQ' | 'DAST';
export type CheckStatus =
  'PASS' | 'WARN' | 'FAIL' | 'BLOCKED' | 'NOT_REQUIRED' | 'SKIP' | 'NO_DATA';
export type EvidenceScanner = 'SAST' | 'DAST' | 'SONARQUBE' | 'NEXUS_IQ';

export const REGIONS: Region[] = ['RD', 'QC'];
export const TEST_STAGES: TestStage[] = ['SMOKE', 'REGRESSION', 'PERFORMANCE'];
export const SCANNERS: Scanner[] = ['SAST', 'SCA', 'NEXUS_IQ', 'DAST'];

export interface ProductSummary {
  id: number;
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  serviceCount: number;
  pipelineCount: number;
  activePipelineCount: number;
  updatedAt: string;
}

export interface ToolCommand {
  tasks: string[];
  flags: string[];
  directory: string | null;
  mavenHome: string | null;
  environment: string[];
}

export interface BuildSettings {
  tool: BuildTool;
  sourceDir: string;
  javaPath: string | null;
  autoSetup: boolean;
  buildPath: string | null;
  command: ToolCommand;
}

export interface UnitTestSettings {
  command: ToolCommand;
  resultPattern: string | null;
  rootDir: string | null;
  reportOutDir: string | null;
  allowEmptyResults: boolean;
  coverageReportPath: string | null;
}

export interface TestSettings {
  maxParallel: number | null;
  smokeMaxParallel: number | null;
  regressionMaxParallel: number | null;
  performanceMaxParallel: number | null;
}

export interface TestJob {
  stage: TestStage;
  name: string | null;
  type: TestJobType | null;
  job: string;
  timeoutMinutes: number | null;
  parameters: string | null;
  remoteJenkins: string | null;
  remoteJenkinsUrl: string | null;
  credentialsId: string | null;
}

export interface DeploymentSettings {
  target: DeployTarget;
  appName: string | null;
  artifactName: string | null;
  baseArtifactName: string | null;
}

export interface UrbanCodeSettings {
  siteName: string | null;
  deployProcess: string | null;
  skipWait: boolean;
  deployWithSnapshot: boolean;
  updateSnapshotComponents: boolean;
  includeOnlyDeployVersions: boolean;
  deployOnlyChanged: boolean;
  deployDescription: string | null;
  requestProperties: string | null;
}

export interface UrbanCodeComponent {
  componentName: string;
  baseDir: string | null;
  fileIncludePatterns: string | null;
  fileExcludePatterns: string | null;
  versionPrefix: string | null;
  version: string | null;
  incrementalVersion: boolean;
}

export interface UrbanCodeApplicationSettings {
  applicationName: string;
  order: number | null;
  environments: string[];
  snapshotName: string | null;
  components: UrbanCodeComponent[];
}

export interface SshTarget {
  host: string | null;
  user: string | null;
  deployDir: string | null;
  deployScript: string | null;
  versionFile: string | null;
}

export interface OpenShiftTarget {
  projectBuild: string | null;
  buildConfigPath: string | null;
  dockerFilePath: string | null;
  buildContext: string | null;
  addFile: string | null;
  dockerRepoPush: string | null;
  dockerRepoPull: string | null;
  certDir: string | null;
  nexusAuthFile: string | null;
  projectDeployment: string | null;
  deployConfigPath: string | null;
  configPath: string | null;
  skipConfigDeploy: boolean;
  healthCheckUrl: string | null;
  routeHostname: string | null;
  deploymentPath: string | null;
  deploymentRepoUrl: string | null;
  deploymentRepoBranch: string | null;
  deploymentRepoCredentialsId: string | null;
}

export interface AppScanSettings {
  applicationId: string;
  sastScanName: string | null;
  includedDirs: string[];
  excludedDirs: string[];
  compile: boolean;
  sourceCodeOnly: boolean;
  useConfigFile: boolean;
  insecureTls: boolean;
  clientPath: string | null;
  compileCommand: ToolCommand;
  dastEnabled: boolean;
  dastScanName: string | null;
  dastTargetUrl: string | null;
  dastPresenceId: string | null;
}

export interface SonarSettings {
  projectName: string | null;
  projectKey: string | null;
  installationName: string | null;
  credentialsId: string | null;
  authTokenCredentialsId: string | null;
  badgeToken: string | null;
  addBadges: boolean;
  fullBadges: boolean;
  command: ToolCommand;
}

export interface NexusIqSettings {
  application: string | null;
  scanPatterns: string[];
  stage: string;
  failOnNetworkError: boolean;
  scaScanName: string | null;
}

export interface ScmSettings {
  repositoryUrl: string | null;
  credentialsId: string | null;
  authType: BitbucketAuthType;
  type: BitbucketType | null;
  targetBranch: string | null;
  cloneUrl: string | null;
  reviewers: string[];
  apiUrl: string | null;
  workspace: string | null;
  projectKey: string | null;
  repoSlug: string | null;
}

export interface GoldenFixPolicy {
  enabled: boolean;
  onlyDirectDependencies: boolean | null;
  minThreatLevel: number | null;
  ecosystems: string[];
  goldenVersionTypes: string[];
  excludeDirs: string[];
  verifyEnabled: boolean | null;
  verifyMaxAttempts: number | null;
  verifyTimeoutMinutes: number | null;
  verifyMavenCommand: string | null;
  verifyGradleCommand: string | null;
  verifyNpmCommand: string | null;
  verifyPipCommand: string | null;
  verifyPubCommand: string | null;
  commitAuthorName: string | null;
  commitAuthorEmail: string | null;
  timeZone: string | null;
}

export interface MetricsSettings {
  enabled: boolean;
  influxProject: string | null;
  influxEnv: string | null;
}

export interface FlutterSettings {
  platform: FlutterPlatform | null;
  modules: string[];
  testModules: string[];
  testSubmodules: string[];
  testSubplugins: string[];
  signingPasswordCredentialsId: string | null;
  prodLicenseCredentialsId: string | null;
  testLicenseCredentialsId: string | null;
  deliveryGroup: string | null;
  deliveryArtifact: string | null;
  deliveryPlugin: string | null;
  sonarSources: string | null;
  sonarTests: string | null;
  sonarFlutterPlugin: boolean;
  dartAnalyzeCommand: string | null;
  sonarScannerVersion: string | null;
}

export interface ServiceSettings {
  build: BuildSettings;
  unitTests: UnitTestSettings;
  tests: TestSettings;
  testJobs: TestJob[];
  deployment: DeploymentSettings;
  delivery: ToolCommand;
  urbanCode: UrbanCodeSettings;
  urbanCodeApplications: UrbanCodeApplicationSettings[];
  sshTargets: Partial<Record<Region, SshTarget>>;
  openShiftTargets: Partial<Record<Region, OpenShiftTarget>>;
  appScan: AppScanSettings;
  sonar: SonarSettings;
  nexusIq: NexusIqSettings;
  scm: ScmSettings;
  goldenFix: GoldenFixPolicy;
  metrics: MetricsSettings;
  flutter: FlutterSettings | null;
}

export interface Service extends ServiceSettings {
  id: number;
  name: string;
  description: string | null;
}

export interface ServiceRequest extends ServiceSettings {
  id: number | null;
  name: string;
  description: string | null;
}

export interface AppScanAccount {
  keyId: string;
  secretCredentialsId: string | null;
}

export interface ProductFields {
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  contactEmail: string | null;
  appScan: AppScanAccount;
}

export interface Product extends ProductFields {
  id: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  services: Service[];
}

export interface ProductRequest extends ProductFields {
  version: number | null;
  services: ServiceRequest[];
}

export interface PipelineKey {
  id: number;
  value: string | null;
  hint: string;
  status: KeyStatus;
  issuedAt: string;
  revokedAt: string | null;
  revokeReason: string | null;
  lastUsedAt: string | null;
}

export interface Pipeline {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  serviceId: number;
  serviceName: string;
  type: PipelineType;
  entryPoint: string;
  agentLabels: string[];
  extendedPipelineJob: string | null;
  securityPipelineJob: string | null;
  jenkinsJob: string | null;
  jenkinsJobUrl: string | null;
  description: string | null;
  enabled: boolean;
  activeKey: PipelineKey | null;
  influxProjectTag: string;
  influxEnv: string;
  createdAt: string;
  updatedAt: string;
  keys: PipelineKey[] | null;
}

export interface PipelineRequest {
  type: PipelineType;
  agentLabels: string[];
  extendedPipelineJob: string | null;
  securityPipelineJob: string | null;
  jenkinsJob: string | null;
  description: string | null;
}

export interface ServicePipelines {
  serviceId: number;
  serviceName: string;
  description: string | null;
  buildTool: BuildTool;
  deployTarget: DeployTarget;
  pipelines: Pipeline[];
}

export interface PlatformSettings {
  jenkinsUrl: string | null;
  jenkinsLibrary: string;
  asocUrl: string;
  appScanClientLinuxUrl: string;
  appScanClientWindowsUrl: string;
  proxyHost: string | null;
  proxyPort: number | null;
  proxyUser: string | null;
  oisHost: string | null;
  sonarServerUrl: string;
  sonarInstallationName: string;
  nexusIqServerUrl: string;
  nexusIqCredentialsId: string;
  nexusSnapshotRepositoryUrl: string | null;
  nexusSnapshotRepositoryId: string | null;
  influxWriteUrl: string | null;
  influxCredentialsId: string | null;
  iosBuildAgent: string | null;
}

export interface DeploymentDefaults {
  urbanCodeSiteName: string;
  urbanCodeDeployProcess: string;
  rdHost: string;
  qcHost: string;
  sshUser: string;
  deployScript: string;
  versionFile: string;
}

export interface SeverityLimits {
  maxCritical: number;
  maxHigh: number;
  maxMedium: number;
}

export interface ScanSettings {
  coverageMinLine: number;
  sastPrepareTimeoutMinutes: number;
  sastPollTimeoutMinutes: number;
  sastPollIntervalSeconds: number;
  scaEnabled: boolean;
  scaPollTimeoutMinutes: number;
  scaPollIntervalSeconds: number;
  dastPollTimeoutMinutes: number;
  dastPollIntervalSeconds: number;
  dastReportTimeoutMinutes: number;
  dastReportIntervalSeconds: number;
  sonarWaitForQualityGate: boolean;
  sonarQualityGateTimeoutMinutes: number;
}

export interface ReleaseGateSettings {
  scanners: Scanner[];
  requireCoverage: boolean;
  stateFile: string;
}

export interface ServiceDefaults {
  buildTool: BuildTool;
  deployTarget: DeployTarget;
  sourceDir: string;
  testsMaxParallel: number;
}

export interface GlobalSettingsValues {
  platform: PlatformSettings;
  deployment: DeploymentDefaults;
  limits: Record<Scanner, SeverityLimits>;
  scans: ScanSettings;
  releaseGate: ReleaseGateSettings;
  serviceDefaults: ServiceDefaults;
  goldenFix: GoldenFixPolicy;
}

export interface GlobalSettings extends GlobalSettingsValues {
  version: number;
  updatedAt: string;
}

export interface GlobalSettingsRequest extends GlobalSettingsValues {
  version: number | null;
}

export interface PipelineRun {
  time: string;
  result: RunResult;
  branch: string | null;
  build: number | null;
  durationSeconds: number | null;
  commit: string | null;
  job: string | null;
  buildUrl: string | null;
  stagesTotal: number | null;
  passed: number | null;
  warned: number | null;
  failed: number | null;
  blocked: number | null;
  skipped: number | null;
}

export interface DailyActivity {
  date: string;
  runs: number;
  failures: number;
  deployments: number;
}

export interface DoraSummary {
  rangeDays: number;
  runs: number;
  deployments: number;
  deploymentsPerWeek: number | null;
  deploymentFrequencyLevel: DoraLevel | null;
  leadTimeMedianSeconds: number | null;
  leadTimeLevel: DoraLevel | null;
  changeFailureRatePercent: number | null;
  changeFailureRateLevel: DoraLevel | null;
  meanTimeToRestoreSeconds: number | null;
  timeToRestoreLevel: DoraLevel | null;
  restores: number;
  failingSince: string | null;
  averageDurationSeconds: number | null;
  daily: DailyActivity[];
}

export interface MonitoringStatus {
  influxConfigured: boolean;
  influxReachable: boolean;
  influxError: string | null;
  grafanaConfigured: boolean;
  grafanaUrl: string | null;
}

export interface ProductHealth {
  productId: number;
  code: string;
  name: string;
  ownerTeam: string | null;
  serviceCount: number;
  pipelineCount: number;
  overall: RunResult;
  statusCounts: Partial<Record<RunResult, number>>;
  lastRunAt: string | null;
}

export interface MonitoringOverview {
  products: ProductHealth[];
  metricsError: string | null;
}

export interface PipelineHealth {
  pipeline: Pipeline;
  status: RunResult;
  lastRun: PipelineRun | null;
}

export interface ProductMonitoring {
  productId: number;
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  overall: RunResult;
  pipelines: PipelineHealth[];
  metricsError: string | null;
}

export interface GrafanaPanel {
  id: number;
  title: string;
  width: number;
  url: string;
}

export interface PipelineMonitoring {
  pipeline: Pipeline;
  status: RunResult;
  lastRun: PipelineRun | null;
  dora: DoraSummary;
  recentRuns: PipelineRun[];
  grafana: { dashboardUrl: string; panels: GrafanaPanel[] } | null;
  metricsError: string | null;
}

export interface ProductEvidence {
  productId: number;
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  contactEmail: string | null;
  services: ServiceEvidence[];
  metricsError: string | null;
}

export interface ServiceEvidence {
  serviceId: number;
  name: string;
  description: string | null;
  repositoryUrl: string | null;
  artifactName: string | null;
  appScanApplicationId: string | null;
  sonarProjectKey: string | null;
  nexusIqApplication: string | null;
  pipelines: PipelineEvidence[];
}

export interface PipelineEvidence {
  pipelineId: number;
  type: PipelineType;
  enabled: boolean;
  jenkinsJobUrl: string | null;
  status: RunResult;
  run: RunEvidence | null;
}

export interface RunEvidence {
  build: BuildEvidence;
  coverage: CoverageEvidence | null;
  testSuites: TestSuiteEvidence[];
  scans: ScanEvidence[];
  releaseGate: ReleaseGateEvidence | null;
  stages: StageEvidence[];
}

export interface BuildEvidence {
  number: number | null;
  finishedAt: string | null;
  result: RunResult;
  branch: string | null;
  commit: string | null;
  durationSeconds: number | null;
  job: string | null;
  url: string | null;
  reportUrl: string | null;
  testReportUrl: string | null;
  artifactsUrl: string | null;
}

export interface CoverageEvidence {
  status: CheckStatus;
  linePercent: number | null;
  requiredPercent: number | null;
  coveredLines: number | null;
  totalLines: number | null;
}

export interface TestSuiteEvidence {
  stage: TestStage;
  status: CheckStatus;
  jobs: number | null;
  passed: number | null;
  failed: number | null;
  notConfigured: number | null;
  durationMs: number | null;
}

export interface ScanEvidence {
  scanner: EvidenceScanner;
  status: CheckStatus;
  critical: number | null;
  high: number | null;
  medium: number | null;
  low: number | null;
  maxCritical: number | null;
  maxHigh: number | null;
  maxMedium: number | null;
  link: string | null;
}

export interface ReleaseGateEvidence {
  allowed: boolean;
  violations: number | null;
  reason: string | null;
}

export interface StageEvidence {
  name: string;
  status: CheckStatus;
  durationSeconds: number | null;
  reason: string | null;
}

export interface FieldProblem {
  field: string;
  message: string;
}

export const PIPELINE_TYPES: { value: PipelineType; label: string; description: string }[] = [
  {
    value: 'FULL',
    label: 'Full',
    description: 'Build, scans, tests, deployment and release (devSecOpsPipeline)',
  },
  {
    value: 'SECURITY',
    label: 'Security',
    description: 'Build and security scans, optionally starts the extended pipeline',
  },
  {
    value: 'EXTENDED',
    label: 'Extended',
    description: 'Deployment and tests started by the security pipeline',
  },
  { value: 'SAST', label: 'SAST scanning', description: 'AppScan static scan of the sources only' },
];

export const DEFAULT_JENKINS_LIBRARY = 'DevSecOpsJenkinsLibrary';
