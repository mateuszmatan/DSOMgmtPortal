import {
  BuildTool,
  DeployTarget,
  FieldProblem,
  OpenShiftTarget,
  Pipeline,
  PipelineType,
  Product,
  ProductRequest,
  Region,
  Service,
  ServicePipelines,
} from '../core/models';
import { createServiceForm, toServiceRequest } from '../products/product-form-model';

export type OnboardingPipeline = Extract<PipelineType, 'SAST' | 'SECURITY' | 'FULL'>;

export interface Choice<T> {
  value: T;
  label: string;
  description: string;
  points?: readonly string[];
}

export const PIPELINES: readonly Choice<OnboardingPipeline>[] = [
  {
    value: 'SAST',
    label: 'Static scan',
    description: 'Checks the source code for security flaws. Nothing is built or deployed.',
    points: ['Downloads the code of each service', 'Scans it with HCL AppScan'],
  },
  {
    value: 'SECURITY',
    label: 'Security',
    description: 'Builds every service and runs all the security checks.',
    points: [
      'Builds the code and runs its unit tests',
      'Scans the code, its libraries and its quality',
      'Publishes a checked snapshot to Nexus',
    ],
  },
  {
    value: 'FULL',
    label: 'Full',
    description: 'Everything Security does, then deploys, tests and releases.',
    points: [
      'Everything the Security pipeline does',
      'Deploys to the RD and QC test environments',
      'Runs your smoke, regression and performance tests',
      'Releases an artifact with no known vulnerabilities',
    ],
  },
];

export const TOOLS: readonly Choice<BuildTool>[] = [
  { value: 'GRADLE', label: 'Gradle', description: 'The code has a build.gradle file' },
  { value: 'MAVEN', label: 'Maven', description: 'The code has a pom.xml file' },
];

export const TARGETS: readonly Choice<DeployTarget>[] = [
  {
    value: 'VM',
    label: 'Virtual machines',
    description: 'Installed on BBH servers with UrbanCode Deploy',
  },
  {
    value: 'OPENSHIFT',
    label: 'OpenShift',
    description: 'Runs as a container on the BBH OpenShift platform',
  },
];

export type ProductMode = 'new' | 'existing';

export const PRODUCT_MODES: readonly Choice<ProductMode>[] = [
  { value: 'new', label: 'A new product', description: 'It is not in the portal yet' },
  {
    value: 'existing',
    label: 'A product in the portal',
    description: 'Add services to it or change them',
  },
];

export const OPENSHIFT_PROJECT = /^[a-z0-9]([-a-z0-9]{0,48}[a-z0-9])?$/;
const BUILD_TASKS: Record<BuildTool, string> = {
  GRADLE: 'clean build',
  MAVEN: 'clean verify',
  FLUTTER: '',
};
const MAVEN_ARTIFACT = 'target/*.jar';
const MAVEN_DELIVERY = 'deploy:deploy-file';
const IMAGE_REGISTRY = 'docker-qc.tools.bbh.com';

export function pipelineLabel(pipeline: OnboardingPipeline): string {
  return PIPELINES.find((option) => option.value === pipeline)!.label;
}

export function choiceLabel<T>(choices: readonly Choice<T>[], value: T): string {
  return choices.find((option) => option.value === value)?.label ?? String(value);
}

export function preparation(pipeline: OnboardingPipeline): string[] {
  const needs = [
    "Your product's AppScan API key ID, from the Application Security team",
    'The name and the AppScan application ID of each service',
    'Whether each service is built with Gradle or Maven',
  ];
  return pipeline === 'SAST'
    ? needs
    : [...needs, 'Whether each service runs on virtual machines or OpenShift'];
}

export interface OnboardingService {
  id: number | null;
  name: string;
  description: string;
  appScanId: string;
  tool: BuildTool;
  target: DeployTarget | null;
  openShiftProject: string;
}

export function fromService(service: Service): OnboardingService {
  return {
    id: service.id,
    name: service.name,
    description: service.description ?? '',
    appScanId: service.appScan.applicationId,
    tool: service.build.tool,
    target: service.deployment.target,
    openShiftProject: '',
  };
}

export function deploysWith(
  pipeline: OnboardingPipeline,
  service: OnboardingService,
): DeployTarget | null {
  return pipeline === 'SAST' && service.id === null ? 'VM' : service.target;
}

type TargetText = Partial<Omit<Record<keyof OpenShiftTarget, string>, 'skipConfigDeploy'>>;

export function openShiftTargets(
  project: string,
  service: string,
  tool: BuildTool,
): Record<Region, TargetText> {
  const image = `${IMAGE_REGISTRY}/${project}/${service.toLowerCase()}`;
  const shared = {
    dockerRepoPull: image,
    deployConfigPath: 'openshift/deployment.yaml',
    healthCheckUrl: '/actuator/health',
  };
  return {
    RD: {
      ...shared,
      projectBuild: `${project}-build`,
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: tool === 'MAVEN' ? 'target/docker' : 'build/docker',
      dockerRepoPush: image,
      certDir: '/etc/pki/openshift',
      nexusAuthFile: '/home/jenkins/.docker/nexus-auth.json',
      projectDeployment: `${project}-rd`,
      configPath: 'openshift/config-rd.yaml',
    },
    QC: { ...shared, projectDeployment: `${project}-qc`, configPath: 'openshift/config-qc.yaml' },
  };
}

export function serviceRequest(
  service: OnboardingService,
  pipeline: OnboardingPipeline,
  existing?: Service,
) {
  const form = createServiceForm(existing);
  form.patchValue({
    name: service.name,
    description: service.description,
    appScan: { applicationId: service.appScanId },
  });
  if (!existing) {
    const target = deploysWith(pipeline, service) ?? 'VM';
    const openShift = target === 'OPENSHIFT';
    form.patchValue({
      build: {
        tool: service.tool,
        autoSetup: true,
        buildPath: service.tool === 'MAVEN' ? MAVEN_ARTIFACT : '',
        command: { tasks: BUILD_TASKS[service.tool] },
      },
      deployment: {
        target,
        appName: openShift ? service.name : '',
        artifactName: openShift ? `${service.name}.jar` : '',
      },
      delivery: { tasks: MAVEN_DELIVERY },
      openShiftTargets: openShift
        ? openShiftTargets(service.openShiftProject, service.name, service.tool)
        : {},
    });
  }
  return toServiceRequest(form);
}

export interface NewProduct {
  departmentId: number | null;
  name: string;
  code: string;
  ownerTeam: string;
  contactEmail: string;
  appScanKeyId: string;
}

export function productRequest(
  product: NewProduct | Product,
  services: readonly OnboardingService[],
  pipeline: OnboardingPipeline,
  departmentId: number | null = null,
): ProductRequest {
  const stored = 'id' in product ? product : null;
  const requests = services.map((service) =>
    serviceRequest(
      service,
      pipeline,
      stored?.services.find((candidate) => candidate.id === service.id),
    ),
  );
  if (stored) {
    return {
      code: stored.code,
      name: stored.name,
      description: stored.description,
      ownerTeam: stored.ownerTeam,
      contactEmail: stored.contactEmail,
      departmentId: stored.departmentId ?? departmentId,
      appScan: stored.appScan,
      version: stored.version,
      services: requests,
    };
  }
  const fresh = product as NewProduct;
  return {
    code: fresh.code,
    name: fresh.name.trim(),
    description: null,
    ownerTeam: fresh.ownerTeam.trim() || null,
    contactEmail: fresh.contactEmail.trim() || null,
    departmentId: fresh.departmentId,
    appScan: { keyId: fresh.appScanKeyId.trim(), secretCredentialsId: null },
    version: null,
    services: requests,
  };
}

const FIELD_NAMES: Record<string, string> = {
  name: 'name',
  'appScan.applicationId': 'AppScan application ID',
  'appScan.keyId': 'AppScan API key ID',
  code: 'product code',
};

export function problemText(problem: FieldProblem, services: readonly OnboardingService[]): string {
  const match = /^services\[(\d+)]\.(.+)$/.exec(problem.field);
  if (!match) {
    return `${FIELD_NAMES[problem.field] ?? problem.field}: ${problem.message}`;
  }
  const service = services[Number(match[1])]?.name ?? 'A service';
  return `${service}, ${FIELD_NAMES[match[2]] ?? match[2]}: ${problem.message}`;
}

export interface ServiceStart {
  serviceName: string;
  pipeline: Pipeline | null;
}

export function servicesToStart(
  services: readonly ServicePipelines[],
  pipeline: OnboardingPipeline,
): ServiceStart[] {
  return services.map((service) => ({
    serviceName: service.serviceName,
    pipeline: service.pipelines.find((candidate) => candidate.type === pipeline) ?? null,
  }));
}

export function jobName(pipeline: Pipeline): string {
  return `DevSecOps/${pipeline.productCode}/${pipeline.serviceName}-${pipeline.type.toLowerCase()}`;
}
