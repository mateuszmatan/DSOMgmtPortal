import {
  DoraSummary,
  GlobalSettings,
  MonitoringOverview,
  MonitoringStatus,
  Pipeline,
  PipelineEvidence,
  PipelineHealth,
  PipelineMonitoring,
  PipelineRun,
  ProductHealth,
  ProductMonitoring,
  Product,
  ProductEvidence,
  RunEvidence,
  Service,
  ServiceEvidence,
  ServicePipelines,
  ToolCommand,
} from '../core/models';

export function command(overrides: Partial<ToolCommand> = {}): ToolCommand {
  return { tasks: [], flags: [], directory: null, mavenHome: null, environment: [], ...overrides };
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
        components: [
          {
            componentName: 'CERT-GUI-app',
            baseDir: 'build/libs',
            fileIncludePatterns: '*.jar',
            fileExcludePatterns: null,
            versionPrefix: null,
            version: null,
            incrementalVersion: true,
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
    },
    nexusIq: {
      application: 'cert-gui',
      scanPatterns: ['**/build/libs/*.jar'],
      stage: 'build',
      failOnNetworkError: false,
      scaScanName: null,
    },
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
    metrics: { enabled: true, influxProject: 'CERT-gui', influxEnv: 'test' },
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
      scaEnabled: true,
      scaPollTimeoutMinutes: 40,
      scaPollIntervalSeconds: 30,
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
      durationSeconds: 1325,
      job: 'DevSecOps/CERT/gui-full',
      url: build,
      reportUrl: `${build}Pipeline_20Report/`,
      testReportUrl: `${build}testReport/`,
      artifactsUrl: `${build}artifact/`,
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
        stage: 'SMOKE',
        status: 'PASS',
        jobs: 2,
        passed: 2,
        failed: 0,
        notConfigured: 0,
        durationMs: 95_000,
      },
      {
        stage: 'REGRESSION',
        status: 'WARN',
        jobs: 3,
        passed: 2,
        failed: 1,
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
        link: build,
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
    grafana: {
      dashboardUrl: 'https://grafana.bbh.com/d/dso-dora',
      panels: [{ id: 1, title: 'Deployments', width: 6, url: 'https://grafana.bbh.com/d-solo/1' }],
    },
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

export function monitoringStatus(overrides: Partial<MonitoringStatus> = {}): MonitoringStatus {
  return {
    influxConfigured: true,
    influxReachable: true,
    influxError: null,
    grafanaConfigured: true,
    grafanaUrl: 'https://grafana.bbh.com',
    ...overrides,
  };
}
