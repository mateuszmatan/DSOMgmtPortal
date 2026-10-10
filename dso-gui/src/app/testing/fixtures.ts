import { ProductDetails } from '../../../../beadle-gui/src/app/beadle/product-details-api';
import {
  Department,
  DoraSummary,
  GlobalSettings,
  GoldenFixEvidence,
  MonitoringOverview,
  MonitoringStatus,
  Pipeline,
  PipelineEvidence,
  PipelineHealth,
  PipelineKey,
  PipelineMonitoring,
  PipelineRun,
  PortfolioActivity,
  ProductHealth,
  ProductMonitoring,
  Product,
  ProductEvidence,
  ProductSummary,
  RunEvidence,
  Service,
  ServiceEvidence,
  ServicePipelines,
  ServiceTemplate,
  ToolCommand,
} from '../core/models';

export function command(overrides: Partial<ToolCommand> = {}): ToolCommand {
  return {
    tasks: [],
    flags: [],
    directory: null,
    mavenHome: null,
    environment: [],
    label: null,
    returnStdout: false,
    ...overrides,
  };
}

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
      buildPath: 'build/libs/*.jar',
      command: command({ tasks: ['clean', 'build'], flags: ['--refresh-dependencies'] }),
    },
    unitTests: {
      command: command({ tasks: ['test', 'jacocoTestReport'] }),
      resultPattern: 'build/test-results/test/*.xml',
      rootDir: null,
      reportOutDir: null,
      allowEmptyResults: false,
      coverageReportPath: null,
    },
    tests: {
      maxParallel: null,
      smokeMaxParallel: 10,
      regressionMaxParallel: null,
      performanceMaxParallel: null,
      smokeRequired: true,
      regressionRequired: true,
      performanceRequired: true,
      smokePollIntervalSec: null,
      regressionPollIntervalSec: null,
      performancePollIntervalSec: null,
    },
    testJobs: [
      {
        stage: 'SMOKE',
        name: 'smoke',
        type: 'LOCAL',
        job: 'CERT/gui-smoke',
        timeoutMinutes: 30,
        parameters: null,
        remoteJenkins: null,
        remoteJenkinsUrl: null,
        credentialsId: null,
        pollIntervalSec: null,
        tokenCredentialsId: null,
        abortTriggeredJob: false,
        overrideTrustAllCertificates: false,
        preventRemoteBuildQueue: false,
        trustAllCertificates: false,
        useCrumbCache: false,
        useJobInfoCache: false,
      },
    ],
    deployment: { target: 'VM', appName: null, artifactName: null, baseArtifactName: null },
    delivery: command(),
    urbanCode: {
      siteName: null,
      deployProcess: null,
      skipWait: false,
      deployWithSnapshot: true,
      updateSnapshotComponents: false,
      includeOnlyDeployVersions: true,
      deployOnlyChanged: false,
      deployDescription: null,
      requestProperties: null,
    },
    urbanCodeApplications: [
      {
        applicationName: 'CERT-GUI',
        order: 1,
        environments: ['DV', 'RD'],
        snapshotName: null,
        siteName: null,
        deployProcess: null,
        skipWait: null,
        deployWithSnapshot: null,
        updateSnapshotComponents: null,
        includeOnlyDeployVersions: null,
        deployOnlyChanged: null,
        deployDescription: null,
        description: null,
        requestProperties: null,
        components: [
          {
            componentName: 'CERT-GUI-app',
            baseDir: 'build/libs',
            fileIncludePatterns: '*.jar',
            fileExcludePatterns: null,
            versionPrefix: null,
            version: null,
            incrementalVersion: true,
            extensions: null,
            charset: null,
            pushDescription: null,
            versionProperties: null,
            versionDescription: null,
          },
        ],
      },
    ],
    sshTargets: {
      QC: {
        host: null,
        user: null,
        deployDir: '/opt/cert/gui',
        deployScript: null,
        versionFile: null,
      },
    },
    openShiftTargets: {},
    appScan: {
      applicationId: '109f44ac-cc06-4ca0-884e-d944904f7019',
      sastScanName: null,
      includedDirs: [],
      excludedDirs: [],
      compile: true,
      sourceCodeOnly: false,
      useConfigFile: false,
      insecureTls: false,
      clientPath: null,
      compileCommand: command(),
      dastEnabled: false,
      dastScanName: null,
      dastTargetUrl: null,
      dastPresenceId: null,
      secretCredentialsId: null,
    },
    sonar: {
      projectName: 'CertScanner GUI',
      projectKey: 'cert-gui',
      installationName: null,
      credentialsId: null,
      authTokenCredentialsId: null,
      badgeToken: null,
      addBadges: false,
      fullBadges: false,
      command: command({ tasks: ['sonarqube'] }),
      serverUrl: null,
    },
    nexusIq: { serverUrl: null, credentialsId: null, scaScanName: null },
    nexusIqApplications: [
      {
        application: 'cert-gui',
        scanPatterns: ['**/build/libs/*.jar'],
        stage: 'build',
        failOnNetworkError: false,
      },
    ],
    scm: {
      repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
      credentialsId: 'bitbucket-http-credentials',
      authType: 'BASIC',
      type: null,
      targetBranch: null,
      cloneUrl: null,
      reviewers: [],
      apiUrl: null,
      workspace: null,
      projectKey: null,
      repoSlug: null,
    },
    goldenFix: {
      enabled: null,
      onlyDirectDependencies: null,
      minThreatLevel: null,
      ecosystems: [],
      goldenVersionTypes: [],
      excludeDirs: [],
      verifyEnabled: null,
      verifyMaxAttempts: null,
      verifyTimeoutMinutes: null,
      verifyMavenCommand: null,
      verifyGradleCommand: null,
      verifyNpmCommand: null,
      verifyPipCommand: null,
      verifyPubCommand: null,
      commitAuthorName: null,
      commitAuthorEmail: null,
      timeZone: null,
    },
    metrics: {
      enabled: true,
      influxProject: 'CERT-gui',
      influxEnv: 'test',
      influxUrl: null,
      influxCredentialsId: null,
    },
    flutter: {
      platform: null,
      modules: [],
      testModules: [],
      testSubmodules: [],
      testSubplugins: [],
      signingPasswordCredentialsId: null,
      prodLicenseCredentialsId: null,
      testLicenseCredentialsId: null,
      deliveryGroup: null,
      deliveryArtifact: null,
      deliveryPlugin: null,
      sonarSources: null,
      sonarTests: null,
      sonarFlutterPlugin: false,
      dartAnalyzeCommand: null,
      sonarScannerVersion: null,
    },
    ...overrides,
  };
}

export function anotherService(overrides: Partial<Service> = {}): Service {
  const stored = service();
  return service({
    id: 11,
    name: 'api',
    sonar: { ...stored.sonar, projectKey: 'cert-api' },
    metrics: { ...stored.metrics, influxProject: null },
    ...overrides,
  });
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
    departmentId: 3,
    appScan: { keyId: 'bbh_key', secretCredentialsId: 'hcl-app-scan-account' },
    updatedAt: '2026-10-04T08:00:00Z',
    services: [service()],
    ...overrides,
  };
}

export function productDetails(overrides: Partial<ProductDetails> = {}): ProductDetails {
  return {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    ownerTeam: 'Technology Architecture',
    contactEmail: 'arch@bbh.com',
    departmentId: 3,
    version: 3,
    ...overrides,
  };
}

export function productSummary(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    departmentId: 3,
    departmentName: 'Corporate Technology',
    serviceCount: 2,
    pipelineCount: 3,
    activePipelineCount: 2,
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

export function department(overrides: Partial<Department> = {}): Department {
  return {
    id: 3,
    name: 'Corporate Technology',
    version: 0,
    productCount: 1,
    serviceCount: 2,
    pipelineCount: 3,
    activePipelineCount: 2,
    changeCount: 0,
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
    securityPipelineJob: null,
    jenkinsJob: 'DevSecOps/CERT/gui-full',
    jenkinsJobUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
    description: null,
    enabled: true,
    activeKey: {
      id: 1000,
      value: '6f1c2d3e-0000-4abc-9def-123456789abc',
      hint: '6f1c2d3e…9abc',
      status: 'ACTIVE',
      issuedAt: '2026-10-04T08:00:00Z',
      revokedAt: null,
      revokeReason: null,
      lastUsedAt: null,
    },
    influxProjectTag: 'CERT-gui',
    influxEnv: 'test',
    updatedAt: '2026-10-04T08:00:00Z',
    keys: null,
    version: 3,
    ...overrides,
  };
}

export function serviceTemplate(overrides: Partial<ServiceTemplate> = {}): ServiceTemplate {
  return {
    version: 0,
    updatedAt: '2026-10-08T12:00:00Z',
    agentLabels: ['linux-agent'],
    jenkinsJob: 'DevSecOps/{CODE}/{service}-{type}',
    gradleTasks: 'clean build',
    gradleArtifact: 'build/libs/*.jar',
    gradleScanPattern: '**/build/libs/*.jar',
    mavenTasks: 'clean verify',
    mavenArtifact: 'target/*.jar',
    mavenScanPattern: '**/target/*.jar',
    flutterScanPattern: '**/pubspec.lock',
    deliveryTasks: 'deploy:deploy-file',
    nexusIqApplication: '{code}-{service}',
    repositoryUrl: 'https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}',
    bitbucketCredentialsId: 'bitbucket-http-credentials',
    openShiftProject: '{code}-{service}',
    imageRegistry: 'docker-qc.tools.bbh.com',
    healthCheckUrl: '/actuator/health',
    ...overrides,
  };
}

export function revokedKey(overrides: Partial<PipelineKey> = {}): PipelineKey {
  return {
    id: 999,
    value: null,
    hint: '1a2b3c4d…eeff',
    status: 'REVOKED',
    issuedAt: '2026-09-01T08:00:00Z',
    revokedAt: '2026-10-01T09:30:00Z',
    revokeReason: 'Leaked in a build log',
    lastUsedAt: '2026-09-30T10:00:00Z',
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

export function globalSettings(overrides: Partial<GlobalSettings> = {}): GlobalSettings {
  const zero = { maxCritical: 0, maxHigh: 0, maxMedium: 0 };
  return {
    version: 4,
    updatedAt: '2026-10-04T08:00:00Z',
    platform: {
      jenkinsUrl: 'https://jenkins.bbh.com',
      jenkinsLibrary: 'DevSecOpsJenkinsLibrary',
      asocUrl: 'https://bbh.cloud.appscan.com',
      appScanClientLinuxUrl: 'https://tools.bbh.com/nexus/SAClientUtil_Linux.zip',
      appScanClientWindowsUrl: 'https://tools.bbh.com/nexus/SAClientUtil_Win.zip',
      proxyHost: 'tstproxy.bbh.com',
      proxyPort: 9090,
      proxyUser: 'PROXY_ASOCJenk',
      oisHost: 'oisapi.bbh.com',
      sonarServerUrl: 'https://tools.bbh.com/sonar',
      sonarInstallationName: 'SonarQube',
      nexusIqServerUrl: 'https://tools.bbh.com/IQ',
      nexusIqCredentialsId: 'nexusiqP',
      nexusSnapshotRepositoryUrl: 'http://tools.bbh.com/nexus/content/repositories/snapshots/',
      nexusSnapshotRepositoryId: 'bbh-snapshots',
      influxWriteUrl: 'http://qcwsecopsmon1.testbbh.com:8086/api/v2/write',
      influxCredentialsId: 'influxdb-token',
      iosBuildAgent: 'mac002.bbh.com',
    },
    deployment: {
      urbanCodeSiteName: 'deploy.bbh.com',
      urbanCodeDeployProcess: 'tomcat-app-process',
      rdHost: 'rdltaapps1.testbbh.com',
      qcHost: 'qcltaapps1.testbbh.com',
      sshUser: 'taadmin',
      deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
      versionFile: 'scripts/deployment/version.properties',
    },
    limits: {
      SAST: { ...zero },
      SCA: { ...zero },
      NEXUS_IQ: { maxCritical: 0, maxHigh: 2, maxMedium: 10 },
      DAST: { ...zero },
    },
    scans: {
      coverageMinLine: 60,
      sastPrepareTimeoutMinutes: 120,
      sastPollTimeoutMinutes: 50,
      sastPollIntervalSeconds: 30,
      dastPollTimeoutMinutes: 60,
      dastPollIntervalSeconds: 60,
      dastReportTimeoutMinutes: 30,
      dastReportIntervalSeconds: 30,
      sonarWaitForQualityGate: true,
      sonarQualityGateTimeoutMinutes: 5,
    },
    releaseGate: {
      scanners: ['SAST', 'SCA', 'NEXUS_IQ', 'DAST'],
      requireCoverage: true,
      stateFile: 'release-gate.json',
    },
    serviceDefaults: {
      buildTool: 'MAVEN',
      deployTarget: 'OPENSHIFT',
      sourceDir: 'app',
      testsMaxParallel: 20,
    },
    goldenFix: {
      enabled: true,
      onlyDirectDependencies: true,
      minThreatLevel: 2,
      ecosystems: ['maven', 'npm', 'pypi'],
      goldenVersionTypes: [
        'recommended-non-breaking-with-dependencies',
        'recommended-non-breaking',
      ],
      excludeDirs: [],
      verifyEnabled: true,
      verifyMaxAttempts: 3,
      verifyTimeoutMinutes: 20,
      verifyMavenCommand: null,
      verifyGradleCommand: null,
      verifyNpmCommand: null,
      verifyPipCommand: null,
      verifyPubCommand: null,
      commitAuthorName: 'DevSecOps GoldenFix',
      commitAuthorEmail: 'devsecops-goldenfix@noreply.local',
      timeZone: null,
    },
    ...overrides,
  };
}

export function runEvidence(overrides: Partial<RunEvidence> = {}): RunEvidence {
  const build = 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/42/';
  return {
    build: {
      number: 42,
      finishedAt: '2026-10-04T08:30:00Z',
      result: 'SUCCESS',
      branch: 'release/2.4',
      commit: '9f2c1e7b4d3a5f6e7d8c9b0a1f2e3d4c5b6a7980',
      artifactVersion: '2.4.0-42',
      durationSeconds: 1325,
      job: 'DevSecOps/CERT/gui-full',
      url: build,
      reportUrl: `${build}Pipeline_20Report/`,
      testReportUrl: `${build}testReport/`,
      artifactsUrl: `${build}artifact/`,
      configRenderedAt: '2026-10-04T07:55:00Z',
      configSha256: '3b7e1f0a9c2d4e5f',
    },
    coverage: {
      status: 'PASS',
      linePercent: 84.25,
      requiredPercent: 60,
      coveredLines: 1685,
      totalLines: 2000,
    },
    testSuites: [
      {
        suite: 'UNIT',
        status: 'PASS',
        total: 412,
        passed: 410,
        failed: 0,
        skipped: 2,
        notConfigured: 0,
        durationMs: 240_000,
      },
      {
        suite: 'SMOKE',
        status: 'PASS',
        total: 2,
        passed: 2,
        failed: 0,
        skipped: null,
        notConfigured: 0,
        durationMs: 95_000,
      },
      {
        suite: 'REGRESSION',
        status: 'WARN',
        total: 3,
        passed: 2,
        failed: 1,
        skipped: null,
        notConfigured: 0,
        durationMs: 1_800_000,
      },
    ],
    scans: [
      {
        scanner: 'SAST',
        status: 'PASS',
        critical: 0,
        high: 0,
        medium: 3,
        low: 12,
        maxCritical: 0,
        maxHigh: 0,
        maxMedium: 5,
        qualityGate: null,
        link: `${build}artifact/appscan/sast-report.html`,
      },
      {
        scanner: 'SONARQUBE',
        status: 'PASS',
        critical: null,
        high: null,
        medium: null,
        low: null,
        maxCritical: null,
        maxHigh: null,
        maxMedium: null,
        qualityGate: 'OK',
        link: 'https://tools.bbh.com/sonar/dashboard?id=cert-gui',
      },
      {
        scanner: 'NEXUS_IQ',
        status: 'FAIL',
        critical: 1,
        high: 2,
        medium: 0,
        low: 0,
        maxCritical: 0,
        maxHigh: 2,
        maxMedium: 10,
        qualityGate: null,
        link: build,
      },
    ],
    releaseGate: {
      allowed: false,
      violations: 1,
      reason: 'Nexus IQ: 1 critical above the limit of 0',
    },
    stages: [
      { name: 'Build', status: 'PASS', durationSeconds: 125, reason: null },
      { name: 'Unit tests', status: 'PASS', durationSeconds: 240, reason: null },
      { name: 'Nexus IQ', status: 'FAIL', durationSeconds: 60, reason: '1 critical finding' },
      { name: 'Deploy QC', status: 'BLOCKED', durationSeconds: null, reason: 'Release gate' },
    ],
    goldenFix: null,
    ...overrides,
  };
}

export function goldenFixEvidence(overrides: Partial<GoldenFixEvidence> = {}): GoldenFixEvidence {
  return {
    status: 'PR_CREATED',
    offered: 3,
    applied: 2,
    unresolved: 1,
    pullRequestRaised: true,
    pullRequestUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui/pull-requests/17',
    pullRequestTitle: 'GoldenFix-202610040815',
    ...overrides,
  };
}

export function pipelineEvidence(overrides: Partial<PipelineEvidence> = {}): PipelineEvidence {
  return {
    pipelineId: 100,
    type: 'FULL',
    enabled: true,
    jenkinsJobUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
    status: 'SUCCESS',
    run: runEvidence(),
    ...overrides,
  };
}

export function serviceEvidence(overrides: Partial<ServiceEvidence> = {}): ServiceEvidence {
  return {
    serviceId: 10,
    name: 'gui',
    description: 'Angular front end',
    repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
    artifactName: 'cert-gui.jar',
    appScanApplicationId: '109f44ac-cc06-4ca0-884e-d944904f7019',
    sonarProjectKey: 'cert-gui',
    nexusIqApplication: null,
    pipelines: [pipelineEvidence()],
    ...overrides,
  };
}

export function productEvidence(overrides: Partial<ProductEvidence> = {}): ProductEvidence {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    contactEmail: 'arch@bbh.com',
    services: [serviceEvidence()],
    metricsError: null,
    ...overrides,
  };
}

export function pipelineRun(overrides: Partial<PipelineRun> = {}): PipelineRun {
  return {
    time: '2026-10-04T07:30:00Z',
    result: 'SUCCESS',
    branch: 'develop',
    build: 42,
    durationSeconds: 900,
    commit: 'a28ef0054e42c0ffee',
    job: 'DevSecOps/CERT/gui-full/develop',
    buildUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    stagesTotal: 12,
    passed: 10,
    warned: 1,
    failed: 1,
    blocked: 0,
    skipped: 0,
    ...overrides,
  };
}

export function doraSummary(overrides: Partial<DoraSummary> = {}): DoraSummary {
  return {
    rangeDays: 30,
    runs: 40,
    deployments: 12,
    deploymentsPerWeek: 2.8,
    deploymentFrequencyLevel: 'HIGH',
    leadTimeMedianSeconds: 5400,
    leadTimeLevel: 'ELITE',
    changeFailureRatePercent: 12.5,
    changeFailureRateLevel: 'HIGH',
    meanTimeToRestoreSeconds: 7200,
    timeToRestoreLevel: 'HIGH',
    restores: 3,
    failingSince: null,
    averageDurationSeconds: 900,
    daily: [
      { date: '2026-10-03', runs: 3, failures: 1, deployments: 1 },
      { date: '2026-10-04', runs: 2, failures: 0, deployments: 2 },
    ],
    ...overrides,
  };
}

export function monitoringPipeline(overrides: Partial<Pipeline> = {}): Pipeline {
  const stored = pipeline();
  return pipeline({
    activeKey: { ...stored.activeKey!, value: null },
    keys: [],
    ...overrides,
  });
}

export function pipelineMonitoring(
  overrides: Partial<PipelineMonitoring> = {},
): PipelineMonitoring {
  return {
    pipeline: monitoringPipeline(),
    status: 'SUCCESS',
    lastRun: pipelineRun(),
    dora: doraSummary(),
    recentRuns: [pipelineRun(), pipelineRun({ build: 41, buildUrl: null, result: 'FAILURE' })],
    grafana: [
      {
        name: 'Grafana',
        dashboardUrl: 'https://grafana.bbh.com/d/adzfc54123/pipeline?var-project=CERT-gui',
      },
    ],
    metricsError: null,
    ...overrides,
  };
}

export function pipelineHealth(overrides: Partial<PipelineHealth> = {}): PipelineHealth {
  return {
    pipeline: monitoringPipeline(),
    status: 'SUCCESS',
    lastRun: pipelineRun(),
    ...overrides,
  };
}

export function productMonitoring(overrides: Partial<ProductMonitoring> = {}): ProductMonitoring {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    overall: 'SUCCESS',
    pipelines: [pipelineHealth()],
    metricsError: null,
    ...overrides,
  };
}

export function productHealth(overrides: Partial<ProductHealth> = {}): ProductHealth {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    ownerTeam: 'Technology Architecture',
    departmentId: 3,
    serviceCount: 2,
    pipelineCount: 3,
    overall: 'SUCCESS',
    statusCounts: { SUCCESS: 2, FAILURE: 1 },
    lastRunAt: '2026-10-04T07:30:00Z',
    ...overrides,
  };
}

export function monitoringOverview(
  overrides: Partial<MonitoringOverview> = {},
): MonitoringOverview {
  return { products: [productHealth()], metricsError: null, ...overrides };
}

export function portfolioActivity(overrides: Partial<PortfolioActivity> = {}): PortfolioActivity {
  return { pipelines: 3, dora: doraSummary(), metricsError: null, ...overrides };
}

export function monitoringStatus(overrides: Partial<MonitoringStatus> = {}): MonitoringStatus {
  return {
    influxConfigured: true,
    influxReachable: true,
    influxError: null,
    ...overrides,
  };
}
