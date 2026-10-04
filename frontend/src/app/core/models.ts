/** Types of the portal REST API, mirroring the Spring Boot DTOs. */

export type BuildTool = 'GRADLE' | 'MAVEN' | 'FLUTTER';
export type DeployTarget = 'VM' | 'OPENSHIFT';
export type PipelineType = 'FULL' | 'SECURITY' | 'EXTENDED' | 'SAST';
export type KeyStatus = 'ACTIVE' | 'REVOKED';
export type RunResult = 'SUCCESS' | 'UNSTABLE' | 'FAILURE' | 'ABORTED' | 'NOT_BUILT' | 'NO_DATA' | 'DISABLED';
export type DoraLevel = 'ELITE' | 'HIGH' | 'MEDIUM' | 'LOW';

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

export interface ServiceFields {
  name: string;
  description: string | null;
  buildTool: BuildTool;
  deployTarget: DeployTarget;
  sourceDir: string | null;
  javaPath: string | null;
  buildToolAutoSetup: boolean;
  appScanAppId: string;
  sastScanName: string | null;
  dastEnabled: boolean;
  dastTargetUrl: string | null;
  dastPresenceId: string | null;
  sonarProjectName: string | null;
  sonarProjectKey: string | null;
  nexusIqApplication: string | null;
  nexusIqScanPatterns: string[];
  repositoryUrl: string | null;
  bitbucketCredentialsId: string | null;
  goldenFixEnabled: boolean;
  metricsEnabled: boolean;
  influxProject: string | null;
  influxEnv: string | null;
  appName: string | null;
  artifactName: string | null;
  additionalConfig: string | null;
}

export interface Service extends ServiceFields {
  id: number;
}

export interface ServiceRequest extends ServiceFields {
  id: number | null;
}

export interface ProductFields {
  code: string;
  name: string;
  description: string | null;
  ownerTeam: string | null;
  contactEmail: string | null;
  asocKeyId: string;
  asocSecretCredentialsId: string | null;
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
  value: string;
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

export interface PipelineRun {
  time: string;
  result: RunResult;
  branch: string | null;
  build: number | null;
  durationSeconds: number | null;
  commit: string | null;
  job: string | null;
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

export interface FieldProblem {
  field: string;
  message: string;
}

export const PIPELINE_TYPES: { value: PipelineType; label: string; description: string }[] = [
  { value: 'FULL', label: 'Full', description: 'Build, scans, tests, deployment and release (devSecOpsPipeline)' },
  { value: 'SECURITY', label: 'Security', description: 'Build and security scans, optionally starts the extended pipeline' },
  { value: 'EXTENDED', label: 'Extended', description: 'Deployment and tests started by the security pipeline' },
  { value: 'SAST', label: 'SAST scanning', description: 'AppScan static scan of the sources only' },
];
