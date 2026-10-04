import { Pipeline, Product, Service, ServicePipelines } from '../core/models';

/** API responses for the specs; every builder takes values that override its defaults. */

export function service(overrides: Partial<Service> = {}): Service {
  return {
    id: 10,
    name: 'gui',
    description: 'Angular front end',
    build: {
      tool: 'GRADLE',
      sourceDir: '.',
      javaPath: '/usr/lib/jvm/java-17-openjdk',
      autoSetup: false,
    },
    deployment: { target: 'VM', appName: null, artifactName: null },
    appScan: {
      applicationId: '109f44ac-cc06-4ca0-884e-d944904f7019',
      sastScanName: null,
      dastEnabled: false,
      dastTargetUrl: null,
      dastPresenceId: null,
    },
    sonar: { projectName: 'CertScanner GUI', projectKey: 'cert-gui' },
    nexusIq: { application: 'cert-gui', scanPatterns: ['**/build/libs/*.jar'] },
    scm: {
      repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
      credentialsId: null,
      goldenFixEnabled: true,
    },
    metrics: { enabled: true, influxProject: 'CERT-gui', influxEnv: 'test' },
    additionalConfig: { yaml: null },
    ...overrides,
  };
}

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    version: 3,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    contactEmail: 'arch@bbh.com',
    appScan: { keyId: 'bbh_key', secretCredentialsId: 'hcl-app-scan-account' },
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-04T08:00:00Z',
    services: [service()],
    ...overrides,
  };
}

export function pipeline(overrides: Partial<Pipeline> = {}): Pipeline {
  return {
    id: 100,
    productId: 1,
    productCode: 'CERT',
    productName: 'CertScanner',
    serviceId: 10,
    serviceName: 'gui',
    type: 'FULL',
    entryPoint: 'devSecOpsPipeline',
    agentLabels: ['linux-agent'],
    extendedPipelineJob: null,
    description: null,
    enabled: true,
    activeKey: {
      id: 1000,
      value: '6f1c2d3e-0000-4abc-9def-123456789abc',
      status: 'ACTIVE',
      issuedAt: '2026-10-04T08:00:00Z',
      revokedAt: null,
      revokeReason: null,
      lastUsedAt: null,
    },
    influxProjectTag: 'CERT-gui',
    influxEnv: 'test',
    createdAt: '2026-10-04T08:00:00Z',
    updatedAt: '2026-10-04T08:00:00Z',
    keys: null,
    ...overrides,
  };
}

export function servicePipelines(overrides: Partial<ServicePipelines> = {}): ServicePipelines {
  return {
    serviceId: 10,
    serviceName: 'gui',
    description: 'Angular front end',
    buildTool: 'GRADLE',
    deployTarget: 'VM',
    pipelines: [pipeline()],
    ...overrides,
  };
}
