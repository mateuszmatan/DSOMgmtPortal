import {
  AppScanAccount,
  BuildTool,
  DeployTarget,
  FieldProblem,
  OpenShiftTarget,
  PIPELINE_TYPES,
  Pipeline,
  PipelineType,
  Product,
  ProductRequest,
  Region,
  Service,
  ServicePipelines,
  ServiceTemplateValues,
  pipelineTypeLabel,
  pipelineTypeSlug,
} from '../core/models';
import {
  ServiceForm,
  createNexusIqApplicationForm,
  createServiceForm,
  toServiceRequest,
} from '../products/product-form-model';
import { Choice } from '../shared/choice-tiles';
import { buildDefaults, fillTemplate } from '../shared/service-template';

export type WizardPipeline = Extract<PipelineType, 'SAST' | 'NEXUS_IQ' | 'SECURITY' | 'FULL'>;

export const PIPELINES: readonly Choice<WizardPipeline>[] = [
  {
    value: 'SAST',
    label: 'Static scan',
    description: 'Checks the source code for security flaws. Nothing is built or deployed.',
    points: ['Downloads the code of each service', 'Scans it with HCL AppScan'],
  },
  {
    value: 'NEXUS_IQ',
    label: 'Nexus IQ GoldenFix',
    description:
      'Checks the libraries of each service with Nexus IQ and opens a pull request with safe versions, without deploying anything.',
    points: [
      'Builds the code and scans its libraries',
      'GoldenFix finds safe versions of risky ones',
      'Opens a pull request with them in Bitbucket',
    ],
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

const TOOL_NAMES: readonly Choice<BuildTool>[] = [
  ...TOOLS,
  { value: 'FLUTTER', label: 'Flutter', description: 'The code has a pubspec.yaml file' },
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
    description: 'Add a pipeline or change its services',
  },
];

export const OPENSHIFT_PROJECT = /^[a-z0-9]([-a-z0-9]{0,48}[a-z0-9])?$/;

export function pipelineLabel(pipeline: WizardPipeline): string {
  return PIPELINES.find((option) => option.value === pipeline)!.label;
}

function typeLabel(type: PipelineType): string {
  return PIPELINES.find((option) => option.value === type)?.label ?? pipelineTypeLabel(type);
}

export function pipelineNames(service: ServicePipelines): string {
  const order = (type: PipelineType) => PIPELINE_TYPES.findIndex((option) => option.value === type);
  return service.pipelines
    .map((pipeline) => pipeline.type)
    .sort((one, other) => order(one) - order(other))
    .map(typeLabel)
    .join(', ');
}

function coverage(services: readonly ServicePipelines[], type: PipelineType): string {
  const count = services.filter((service) =>
    service.pipelines.some((pipeline) => pipeline.type === type),
  ).length;
  if (!count) {
    return 'No service has it yet';
  }
  return count === services.length
    ? 'Every service has it'
    : `${count} of ${services.length} services ${count === 1 ? 'has' : 'have'} it`;
}

export function pipelineReach(
  pipeline: WizardPipeline,
  services: readonly WizardService[],
  current: readonly ServicePipelines[] | null,
): string {
  const has = (service: WizardService) =>
    current?.some(
      (stored) =>
        stored.serviceId === service.id &&
        stored.pipelines.some((candidate) => candidate.type === pipeline),
    ) ?? false;
  const gaining = services.filter((service) => !has(service)).map((service) => service.name);
  if (!gaining.length) {
    return `${pipelineLabel(pipeline)} · every service has it already`;
  }
  return gaining.length === services.length
    ? `${pipelineLabel(pipeline)} · added to every service`
    : `${pipelineLabel(pipeline)} · added to ${listed(gaining)}`;
}

function listed(names: readonly string[]): string {
  return names.length > 1 ? `${names.slice(0, -1).join(', ')} and ${names.at(-1)}` : names[0];
}

export function pipelineChoices(
  services: readonly ServicePipelines[] | null,
): readonly Choice<WizardPipeline>[] {
  return services
    ? PIPELINES.map((option) => ({ ...option, note: coverage(services, option.value) }))
    : PIPELINES;
}

export function choiceLabel<T>(choices: readonly Choice<T>[], value: T): string {
  return choices.find((option) => option.value === value)?.label ?? String(value);
}

export function deploys(pipeline: WizardPipeline): boolean {
  return pipeline === 'SECURITY' || pipeline === 'FULL';
}

export function preparation(pipeline: WizardPipeline): string[] {
  const needs = [
    "Your product's AppScan API key ID, from the Application Security team",
    'The name and the AppScan application ID of each service',
    'Whether each service is built with Gradle or Maven',
  ];
  if (pipeline === 'NEXUS_IQ') {
    return [...needs, 'The Nexus IQ application and the Bitbucket repository of each service'];
  }
  return deploys(pipeline)
    ? [...needs, 'Whether each service runs on virtual machines or OpenShift']
    : needs;
}

export interface WizardService {
  id: number | null;
  name: string;
  description: string;
  appScanId: string;
  tool: BuildTool;
  target: DeployTarget | null;
  openShiftProject: string;
  nexusIqApplication: string;
  repositoryUrl: string;
}

export interface WizardDefaults {
  tool: BuildTool | null;
  target: DeployTarget | null;
  template: ServiceTemplateValues | null;
  productCode: string;
}

export interface NamedDefaults {
  openShiftProject: string;
  nexusIqApplication: string;
  repositoryUrl: string;
}

export function namedDefaults(defaults: WizardDefaults, serviceName: string): NamedDefaults {
  const { template, productCode } = defaults;
  const filled = (pattern: string | null | undefined) =>
    productCode && serviceName ? fillTemplate(pattern, productCode, serviceName) : '';
  return {
    openShiftProject: filled(template?.openShiftProject).toLowerCase(),
    nexusIqApplication: filled(template?.nexusIqApplication),
    repositoryUrl: filled(template?.repositoryUrl),
  };
}

const nexusIqApplicationOf = (service?: Service) =>
  service?.nexusIqApplications[0]?.application ?? '';

const repositoryOf = (service?: Service) => service?.scm.repositoryUrl ?? '';

export function fromService(service: Service): WizardService {
  return {
    id: service.id,
    name: service.name,
    description: service.description ?? '',
    appScanId: service.appScan.applicationId,
    tool: service.build.tool,
    target: service.deployment.target,
    openShiftProject: '',
    nexusIqApplication: nexusIqApplicationOf(service),
    repositoryUrl: repositoryOf(service),
  };
}

export function deploysWith(pipeline: WizardPipeline, service: WizardService): DeployTarget | null {
  return !deploys(pipeline) && service.id === null ? 'VM' : service.target;
}

export function serviceSummary(pipeline: WizardPipeline, service: WizardService): string {
  const target = deploysWith(pipeline, service);
  const parts = [choiceLabel(TOOL_NAMES, service.tool)];
  if (target && (deploys(pipeline) || service.id !== null)) {
    parts.push(`runs on ${choiceLabel(TARGETS, target)}`);
  }
  if (target === 'OPENSHIFT' && service.openShiftProject) {
    parts.push(`project ${service.openShiftProject}`);
  }
  if (pipeline === 'NEXUS_IQ' && service.nexusIqApplication) {
    parts.push(`Nexus IQ ${service.nexusIqApplication}`);
  }
  return parts.join(' · ');
}

export function changesOf(
  pipeline: WizardPipeline,
  service: WizardService,
  stored: Service,
): string[] {
  const changes: string[] = [];
  if (service.name !== stored.name) {
    changes.push(`renamed from ${stored.name}`);
  }
  if (service.description !== (stored.description ?? '')) {
    changes.push(service.description ? 'new description' : 'no description');
  }
  if (service.appScanId.toLowerCase() !== stored.appScan.applicationId.toLowerCase()) {
    changes.push('new AppScan application ID');
  }
  if (service.tool !== stored.build.tool) {
    changes.push(
      `built with ${choiceLabel(TOOLS, service.tool)} instead of ${choiceLabel(TOOL_NAMES, stored.build.tool)}, with the default build settings`,
    );
  }
  if (service.target !== stored.deployment.target) {
    const project = service.openShiftProject ? ` in project ${service.openShiftProject}` : '';
    changes.push(
      `runs on ${choiceLabel(TARGETS, service.target)}${project} instead of ${choiceLabel(TARGETS, stored.deployment.target)}, with the default deployment settings`,
    );
  }
  if (pipeline === 'NEXUS_IQ' && service.nexusIqApplication !== nexusIqApplicationOf(stored)) {
    changes.push('new Nexus IQ application');
  }
  if (pipeline === 'NEXUS_IQ' && service.repositoryUrl !== repositoryOf(stored)) {
    changes.push('new Bitbucket repository');
  }
  return changes;
}

const REVIEW_LABELS = ['Added', 'Changed', 'Removed', 'Unchanged'] as const;

type ReviewLabel = (typeof REVIEW_LABELS)[number];

interface ReviewEntry {
  name: string;
  text: string;
}

export interface ReviewGroup {
  label: ReviewLabel;
  services: ReviewEntry[];
}

function reviewEntry(
  pipeline: WizardPipeline,
  service: WizardService,
  stored: readonly Service[],
  removed: readonly number[],
): [ReviewLabel, ReviewEntry] {
  const before = stored.find((candidate) => candidate.id === service.id);
  if (!before) {
    return ['Added', { name: service.name, text: serviceSummary(pipeline, service) }];
  }
  if (removed.includes(before.id)) {
    return ['Removed', { name: service.name, text: serviceSummary(pipeline, service) }];
  }
  const changes = changesOf(pipeline, service, before);
  return changes.length
    ? ['Changed', { name: service.name, text: changes.join('; ') }]
    : ['Unchanged', { name: service.name, text: serviceSummary(pipeline, service) }];
}

export function reviewGroups(
  pipeline: WizardPipeline,
  services: readonly WizardService[],
  stored: readonly Service[],
  removed: readonly number[],
): ReviewGroup[] {
  const entries = services.map((service) => reviewEntry(pipeline, service, stored, removed));
  return REVIEW_LABELS.map((label) => ({
    label,
    services: entries.filter(([group]) => group === label).map(([, entry]) => entry),
  })).filter((group) => group.services.length);
}

type TargetText = Partial<Omit<Record<keyof OpenShiftTarget, string>, 'skipConfigDeploy'>>;

export function openShiftTargets(
  project: string,
  service: string,
  tool: BuildTool,
  template: ServiceTemplateValues | null,
): Record<Region, TargetText> {
  const registry = template?.imageRegistry;
  const image = `${registry ? `${registry}/` : ''}${project}/${service.toLowerCase()}`;
  const shared = {
    dockerRepoPull: image,
    deployConfigPath: 'openshift/deployment.yaml',
    healthCheckUrl: template?.healthCheckUrl ?? '',
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
  service: WizardService,
  pipeline: WizardPipeline,
  template: ServiceTemplateValues | null,
  existing?: Service,
) {
  const target = deploysWith(pipeline, service) ?? 'VM';
  const retooled = existing?.build.tool !== service.tool;
  const moved = existing?.deployment.target !== target;
  const openShift = target === 'OPENSHIFT';
  const build = buildDefaults(template, service.tool);
  const form = createServiceForm(
    existing && {
      ...existing,
      build: retooled ? undefined : existing.build,
      delivery: retooled || moved ? undefined : existing.delivery,
      deployment: moved ? undefined : existing.deployment,
      openShiftTargets: moved ? undefined : existing.openShiftTargets,
    },
  );
  form.patchValue({
    name: service.name,
    description: service.description,
    appScan: { applicationId: service.appScanId },
  });
  if (retooled) {
    form.patchValue({
      build: {
        tool: service.tool,
        autoSetup: true,
        buildPath: build.artifact,
        command: { tasks: build.tasks },
      },
    });
  }
  if (moved) {
    form.patchValue({
      deployment: {
        target,
        appName: openShift ? service.name : '',
        artifactName: openShift ? `${service.name}.jar` : '',
      },
      openShiftTargets: openShift
        ? openShiftTargets(service.openShiftProject, service.name, service.tool, template)
        : {},
    });
  }
  if (retooled || moved) {
    form.patchValue({ delivery: { tasks: template?.deliveryTasks ?? '' } });
  }
  if (pipeline === 'NEXUS_IQ') {
    scanWithNexusIq(form, service, build.scanPattern, template, existing);
  }
  return toServiceRequest(form);
}

function scanWithNexusIq(
  form: ServiceForm,
  service: WizardService,
  scanPattern: string,
  template: ServiceTemplateValues | null,
  existing?: Service,
): void {
  const applications = form.controls.nexusIqApplications;
  if (service.nexusIqApplication !== nexusIqApplicationOf(existing)) {
    if (applications.length) {
      applications.controls[0].controls.application.setValue(service.nexusIqApplication);
    } else {
      applications.push(
        createNexusIqApplicationForm({
          application: service.nexusIqApplication,
          scanPatterns: scanPattern ? [scanPattern] : [],
        }),
      );
    }
  }
  const scm = form.controls.scm.controls;
  if (service.repositoryUrl !== repositoryOf(existing)) {
    scm.repositoryUrl.setValue(service.repositoryUrl);
    if (!scm.credentialsId.value.trim()) {
      scm.credentialsId.setValue(template?.bitbucketCredentialsId ?? '');
    }
  }
}

export interface NewProduct {
  departmentId: number | null;
  name: string;
  code: string;
  ownerTeam: string;
  contactEmail: string;
  appScanKeyId: string;
}

export function appScanAccount(keyId: string): AppScanAccount {
  return { keyId: keyId.trim(), secretCredentialsId: null };
}

export function productRequest(
  product: NewProduct | Product,
  services: readonly WizardService[],
  pipeline: WizardPipeline,
  departmentId: number | null = null,
  template: ServiceTemplateValues | null = null,
): ProductRequest {
  const stored = 'id' in product ? product : null;
  const requests = services.map((service) =>
    serviceRequest(
      service,
      pipeline,
      template,
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
    appScan: appScanAccount(fresh.appScanKeyId),
    version: null,
    services: requests,
  };
}

const FIELD_NAMES: Record<string, string> = {
  name: 'name',
  'appScan.applicationId': 'AppScan application ID',
  'appScan.keyId': 'AppScan API key ID',
  'nexusIqApplications.application': 'Nexus IQ application',
  'nexusIqApplications.scanPatterns': 'Nexus IQ scan patterns',
  'scm.repositoryUrl': 'Bitbucket repository',
  'scm.credentialsId': 'Bitbucket credentials ID',
  code: 'product code',
};

const fieldName = (field: string) => FIELD_NAMES[field.replace(/\[\d+]/g, '')] ?? field;

export function problemText(problem: FieldProblem, services: readonly WizardService[]): string {
  const match = /^services\[(\d+)]\.(.+)$/.exec(problem.field);
  if (!match) {
    return `${fieldName(problem.field)}: ${problem.message}`;
  }
  const service = services[Number(match[1])]?.name ?? 'A service';
  return `${service}, ${fieldName(match[2])}: ${problem.message}`;
}

export interface ServiceStart {
  serviceName: string;
  pipeline: Pipeline | null;
}

export function servicesToStart(
  services: readonly ServicePipelines[],
  pipeline: WizardPipeline,
): ServiceStart[] {
  return services.map((service) => ({
    serviceName: service.serviceName,
    pipeline: service.pipelines.find((candidate) => candidate.type === pipeline) ?? null,
  }));
}

export function jobName(pipeline: Pipeline): string {
  return (
    pipeline.jenkinsJob ??
    `DevSecOps/${pipeline.productCode}/${pipeline.serviceName}-${pipelineTypeSlug(pipeline.type)}`
  );
}
