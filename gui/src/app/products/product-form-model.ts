import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import {
  BitbucketAuthType,
  BitbucketType,
  BuildTool,
  DeployTarget,
  FieldProblem,
  FlutterPlatform,
  FlutterSettings,
  GoldenFixPolicy,
  OpenShiftTarget,
  Product,
  ProductRequest,
  Region,
  ServiceDefaults,
  ServiceRequest,
  SshTarget,
  TestJob,
  TestJobType,
  TestStage,
  ToolCommand,
  UrbanCodeApplicationSettings,
  UrbanCodeComponent,
} from '../core/models';
import {
  HOST_NAME,
  HTTP_URL,
  eachItem,
  flag,
  integer,
  joinLines,
  joinWords,
  lines,
  maxLines,
  maxWords,
  optional,
  requireWhile,
  requiredWhen,
  revalidateOnChange,
  setEnabled,
  text,
  words,
} from '../shared/form-controls';

export { HTTP_URL, applyFieldProblems, controlAt } from '../shared/form-controls';

export const PRODUCT_CODE = /^[A-Z][A-Z0-9_-]{1,49}$/;
export const SERVICE_NAME = /^[a-z0-9][a-z0-9._-]{0,99}$/;
export const UUID =
  /^\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\s*$/;
export const METRICS_TAG = /^[A-Za-z0-9._-]*$/;
export const SONAR_KEY = /^([a-zA-Z0-9_.:-]*[a-zA-Z_.:-][a-zA-Z0-9_.:-]*)?$/;
export const ENV_VARIABLE = /^[A-Za-z_][A-Za-z0-9_]*=.*$/;
export const GIT_URL = /^(https?:\/\/\S+|ssh:\/\/\S+|git@\S+)$/;
export const CLONE_URL = /^(https?:\/\/\S+|ssh:\/\/\S+)$/;
export const UCD_ENVIRONMENT = /^[A-Za-z0-9_-]{1,20}$/;
export const MODULE_FOLDER = /^[A-Za-z0-9._/-]{1,100}$/;
export const NEXUS_STAGE = /^[a-z-]*$/;
export const BADGE_TOKEN = /^[A-Za-z0-9_]*$/;
export const SCANNER_VERSION = /^[0-9A-Za-z._-]*$/;
export const TIME_ZONE = /^[A-Za-z0-9_+/-]*$/;
export const REMEDIATION_TYPE = /^[a-z-]{1,100}$/;
export const GOLDEN_FIX_ECOSYSTEMS = ['maven', 'npm', 'pypi', 'pub'];

const FOLDER = /^[^,]{1,300}$/;
const tokenLines = (value: string) => lines(value, false);
const tokenWords = (value: string) => words(value, false);
const upTo = (length: number) => new RegExp(`^.{1,${length}}$`);

export const NO_COMMAND: ToolCommand = {
  tasks: [],
  flags: [],
  directory: null,
  mavenHome: null,
  environment: [],
};

export function createToolCommandForm(command?: Partial<ToolCommand> | null) {
  return new FormGroup({
    tasks: text(
      joinWords(command?.tasks),
      maxWords(30, false),
      eachItem(tokenWords, upTo(200), 'At most 200 characters per task'),
    ),
    flags: text(
      joinLines(command?.flags),
      maxLines(40, false),
      eachItem(tokenLines, upTo(300), 'At most 300 characters per flag'),
    ),
    directory: text(command?.directory, Validators.maxLength(500)),
    mavenHome: text(command?.mavenHome, Validators.maxLength(500)),
    environment: text(
      joinLines(command?.environment),
      maxLines(30, false),
      eachItem(tokenLines, ENV_VARIABLE, 'Write each variable as NAME=value'),
      eachItem(tokenLines, upTo(500), 'At most 500 characters per variable'),
    ),
  });
}

export type ToolCommandForm = ReturnType<typeof createToolCommandForm>;

export function toToolCommand(form: ToolCommandForm): ToolCommand {
  const v = form.getRawValue();
  return {
    tasks: words(v.tasks, false),
    flags: lines(v.flags, false),
    directory: optional(v.directory),
    mavenHome: optional(v.mavenHome),
    environment: lines(v.environment, false),
  };
}

export function isJobUrl(job: unknown): boolean {
  const value = typeof job === 'string' ? job.trim() : '';
  return value.startsWith('http://') || value.startsWith('https://');
}

export const REMOTE_JENKINS_MESSAGE =
  'Name the remote Jenkins or its URL, or give the job as a full URL';

export function createTestJobForm(job?: Partial<TestJob> | null) {
  const form = new FormGroup({
    stage: new FormControl<TestStage>(job?.stage ?? 'SMOKE', { nonNullable: true }),
    name: text(job?.name, Validators.maxLength(200)),
    type: new FormControl<TestJobType | null>(job?.type ?? null),
    job: text(job?.job, Validators.required, Validators.maxLength(1000)),
    timeoutMinutes: integer(job?.timeoutMinutes, 1, 1440),
    parameters: text(job?.parameters, Validators.maxLength(2000)),
    remoteJenkins: text(
      job?.remoteJenkins,
      Validators.maxLength(200),
      requiredWhen(
        (j) =>
          j['type'] === 'REMOTE' && !isJobUrl(j['job']) && !optional(String(j['remoteJenkinsUrl'])),
        REMOTE_JENKINS_MESSAGE,
      ),
    ),
    remoteJenkinsUrl: text(
      job?.remoteJenkinsUrl,
      Validators.pattern(HTTP_URL),
      Validators.maxLength(1000),
    ),
    credentialsId: text(job?.credentialsId, Validators.maxLength(200)),
  });
  const { type, remoteJenkins, remoteJenkinsUrl } = form.controls;
  revalidateOnChange(type, remoteJenkins);
  revalidateOnChange(form.controls.job, remoteJenkins);
  revalidateOnChange(remoteJenkinsUrl, remoteJenkins);
  remoteJenkins.updateValueAndValidity();
  return form;
}

export type TestJobForm = ReturnType<typeof createTestJobForm>;

export function isRemoteJob(form: TestJobForm): boolean {
  return form.controls.type.value === 'REMOTE' || isJobUrl(form.controls.job.value);
}

export function toTestJob(form: TestJobForm): TestJob {
  const v = form.getRawValue();
  const remote = isRemoteJob(form);
  return {
    stage: v.stage,
    name: optional(v.name),
    type: v.type,
    job: v.job.trim(),
    timeoutMinutes: v.timeoutMinutes,
    parameters: optional(v.parameters),
    remoteJenkins: remote ? optional(v.remoteJenkins) : null,
    remoteJenkinsUrl: remote ? optional(v.remoteJenkinsUrl) : null,
    credentialsId: remote ? optional(v.credentialsId) : null,
  };
}

export function createUrbanCodeComponentForm(component?: Partial<UrbanCodeComponent> | null) {
  return new FormGroup({
    componentName: text(component?.componentName, Validators.required, Validators.maxLength(200)),
    baseDir: text(component?.baseDir, Validators.maxLength(500)),
    fileIncludePatterns: text(component?.fileIncludePatterns, Validators.maxLength(500)),
    fileExcludePatterns: text(component?.fileExcludePatterns, Validators.maxLength(500)),
    versionPrefix: text(component?.versionPrefix, Validators.maxLength(200)),
    version: text(component?.version, Validators.maxLength(200)),
    incrementalVersion: flag(component?.incrementalVersion, true),
  });
}

export type UrbanCodeComponentForm = ReturnType<typeof createUrbanCodeComponentForm>;

export function createUrbanCodeApplicationForm(
  application?: Partial<UrbanCodeApplicationSettings> | null,
) {
  const components = application ? (application.components ?? []) : [null];
  return new FormGroup({
    applicationName: text(
      application?.applicationName,
      Validators.required,
      Validators.maxLength(200),
    ),
    order: integer(application?.order, 1, 999),
    environments: text(
      joinWords(application?.environments, ', '),
      maxWords(20),
      eachItem(words, UCD_ENVIRONMENT, "Use letters, digits, '-' and '_', at most 20 characters"),
    ),
    snapshotName: text(application?.snapshotName, Validators.maxLength(200)),
    components: new FormArray(components.map(createUrbanCodeComponentForm)),
  });
}

export type UrbanCodeApplicationForm = ReturnType<typeof createUrbanCodeApplicationForm>;

function toUrbanCodeApplication(form: UrbanCodeApplicationForm): UrbanCodeApplicationSettings {
  const v = form.getRawValue();
  return {
    applicationName: v.applicationName.trim(),
    order: v.order,
    environments: words(v.environments),
    snapshotName: optional(v.snapshotName),
    components: v.components.map((c) => ({
      componentName: c.componentName.trim(),
      baseDir: optional(c.baseDir),
      fileIncludePatterns: optional(c.fileIncludePatterns),
      fileExcludePatterns: optional(c.fileExcludePatterns),
      versionPrefix: optional(c.versionPrefix),
      version: optional(c.version),
      incrementalVersion: c.incrementalVersion,
    })),
  };
}

export function createSshTargetForm(target?: Partial<SshTarget> | null) {
  return new FormGroup({
    host: text(target?.host, Validators.pattern(HOST_NAME), Validators.maxLength(255)),
    user: text(target?.user, Validators.maxLength(100)),
    deployDir: text(target?.deployDir, Validators.maxLength(500)),
    deployScript: text(target?.deployScript, Validators.maxLength(500)),
    versionFile: text(target?.versionFile, Validators.maxLength(500)),
  });
}

export type SshTargetForm = ReturnType<typeof createSshTargetForm>;

export function createOpenShiftTargetForm(target?: Partial<OpenShiftTarget> | null) {
  const t = target;
  const max = (length: number) => Validators.maxLength(length);
  return new FormGroup({
    projectBuild: text(t?.projectBuild, max(200)),
    buildConfigPath: text(t?.buildConfigPath, max(500)),
    dockerFilePath: text(t?.dockerFilePath, max(500)),
    buildContext: text(t?.buildContext, max(500)),
    addFile: text(t?.addFile, max(500)),
    dockerRepoPush: text(t?.dockerRepoPush, max(500)),
    dockerRepoPull: text(t?.dockerRepoPull, max(500)),
    certDir: text(t?.certDir, max(500)),
    nexusAuthFile: text(t?.nexusAuthFile, max(500)),
    projectDeployment: text(t?.projectDeployment, max(200)),
    deployConfigPath: text(t?.deployConfigPath, max(500)),
    configPath: text(t?.configPath, max(500)),
    skipConfigDeploy: flag(t?.skipConfigDeploy),
    healthCheckUrl: text(t?.healthCheckUrl, max(500)),
    routeHostname: text(t?.routeHostname, max(300)),
    deploymentPath: text(t?.deploymentPath, max(500)),
    deploymentRepoUrl: text(t?.deploymentRepoUrl, Validators.pattern(GIT_URL), max(1000)),
    deploymentRepoBranch: text(t?.deploymentRepoBranch, max(200)),
    deploymentRepoCredentialsId: text(t?.deploymentRepoCredentialsId, max(200)),
  });
}

export type OpenShiftTargetForm = ReturnType<typeof createOpenShiftTargetForm>;

export function goldenFixControls(policy?: Partial<GoldenFixPolicy> | null, complete = false) {
  const required = complete ? [Validators.required] : [];
  const command = (value: string | null | undefined) => text(value, Validators.maxLength(500));
  return {
    enabled: flag(policy?.enabled, true),
    onlyDirectDependencies: new FormControl<boolean | null>(
      policy?.onlyDirectDependencies ?? null,
      required,
    ),
    minThreatLevel: integer(policy?.minThreatLevel, 1, 10, ...required),
    ecosystems: new FormControl<string[]>(policy?.ecosystems ?? [], {
      nonNullable: true,
      validators: required,
    }),
    goldenVersionTypes: text(
      joinLines(policy?.goldenVersionTypes),
      ...required,
      maxLines(10),
      eachItem(lines, REMEDIATION_TYPE, 'Use a Nexus IQ remediation type'),
    ),
    excludeDirs: text(
      joinLines(policy?.excludeDirs),
      maxLines(30),
      eachItem(lines, upTo(200), 'At most 200 characters per folder'),
    ),
    verifyEnabled: new FormControl<boolean | null>(policy?.verifyEnabled ?? null, required),
    verifyMaxAttempts: integer(policy?.verifyMaxAttempts, 1, 10, ...required),
    verifyTimeoutMinutes: integer(policy?.verifyTimeoutMinutes, 1, 240, ...required),
    verifyMavenCommand: command(policy?.verifyMavenCommand),
    verifyGradleCommand: command(policy?.verifyGradleCommand),
    verifyNpmCommand: command(policy?.verifyNpmCommand),
    verifyPipCommand: command(policy?.verifyPipCommand),
    verifyPubCommand: command(policy?.verifyPubCommand),
    commitAuthorName: text(policy?.commitAuthorName, ...required, Validators.maxLength(200)),
    commitAuthorEmail: text(
      policy?.commitAuthorEmail,
      ...required,
      Validators.email,
      Validators.maxLength(320),
    ),
    timeZone: text(policy?.timeZone, Validators.pattern(TIME_ZONE), Validators.maxLength(100)),
  };
}

export type GoldenFixControls = ReturnType<typeof goldenFixControls>;
export type GoldenFixValue = ReturnType<FormGroup<GoldenFixControls>['getRawValue']>;

export function toGoldenFixPolicy(v: GoldenFixValue): GoldenFixPolicy {
  return {
    enabled: v.enabled,
    onlyDirectDependencies: v.onlyDirectDependencies,
    minThreatLevel: v.minThreatLevel,
    ecosystems: [...new Set(v.ecosystems)],
    goldenVersionTypes: lines(v.goldenVersionTypes),
    excludeDirs: lines(v.excludeDirs),
    verifyEnabled: v.verifyEnabled,
    verifyMaxAttempts: v.verifyMaxAttempts,
    verifyTimeoutMinutes: v.verifyTimeoutMinutes,
    verifyMavenCommand: optional(v.verifyMavenCommand),
    verifyGradleCommand: optional(v.verifyGradleCommand),
    verifyNpmCommand: optional(v.verifyNpmCommand),
    verifyPipCommand: optional(v.verifyPipCommand),
    verifyPubCommand: optional(v.verifyPubCommand),
    commitAuthorName: optional(v.commitAuthorName),
    commitAuthorEmail: optional(v.commitAuthorEmail),
    timeZone: optional(v.timeZone),
  };
}

export function inheritedGoldenFix(enabled: boolean): GoldenFixPolicy {
  return {
    enabled,
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
  };
}

export function inheritsGoldenFix(policy?: Partial<GoldenFixPolicy> | null): boolean {
  if (!policy) {
    return true;
  }
  const { enabled, ...overrides } = policy;
  return Object.values(overrides).every(
    (value) => value === null || value === undefined || (Array.isArray(value) && !value.length),
  );
}

export function createServiceGoldenFixForm(policy?: Partial<GoldenFixPolicy> | null) {
  const form = new FormGroup({
    inherit: flag(inheritsGoldenFix(policy)),
    ...goldenFixControls(policy),
  });
  const sync = () => {
    for (const [key, control] of Object.entries(form.controls)) {
      if (key !== 'inherit' && key !== 'enabled') {
        setEnabled(control, !form.controls.inherit.value);
      }
    }
  };
  form.controls.inherit.valueChanges.subscribe(sync);
  sync();
  return form;
}

export type ServiceGoldenFixForm = ReturnType<typeof createServiceGoldenFixForm>;

export function createServiceForm(
  service?: Partial<ServiceRequest>,
  defaults?: ServiceDefaults | null,
) {
  const s = service;
  const max = (length: number) => Validators.maxLength(length);
  const folders = (values: string[] | undefined) =>
    text(
      joinLines(values),
      maxLines(30),
      eachItem(lines, FOLDER, 'One folder per line, without commas'),
    );
  const modules = (values: string[] | undefined) =>
    text(joinLines(values), maxLines(30), eachItem(lines, MODULE_FOLDER, 'Not a folder name'));
  const parallel = (value: number | null | undefined) => integer(value, 1, 100);

  const form = new FormGroup({
    id: new FormControl<number | null>(s?.id ?? null),
    name: text(s?.name, Validators.required, Validators.pattern(SERVICE_NAME)),
    description: text(s?.description, max(2000)),
    build: new FormGroup({
      tool: new FormControl<BuildTool>(s?.build?.tool ?? defaults?.buildTool ?? 'GRADLE', {
        nonNullable: true,
      }),
      sourceDir: text(s?.build?.sourceDir ?? defaults?.sourceDir ?? '.', max(500)),
      javaPath: text(s?.build?.javaPath, max(500)),
      autoSetup: flag(s?.build?.autoSetup),
      buildPath: text(s?.build?.buildPath, max(500)),
      command: createToolCommandForm(s?.build?.command),
    }),
    unitTests: new FormGroup({
      command: createToolCommandForm(s?.unitTests?.command),
      resultPattern: text(s?.unitTests?.resultPattern, max(500)),
      rootDir: text(s?.unitTests?.rootDir, max(500)),
      reportOutDir: text(s?.unitTests?.reportOutDir, max(500)),
      allowEmptyResults: flag(s?.unitTests?.allowEmptyResults),
      coverageReportPath: text(s?.unitTests?.coverageReportPath, max(500)),
    }),
    tests: new FormGroup({
      maxParallel: parallel(s?.tests?.maxParallel),
      smokeMaxParallel: parallel(s?.tests?.smokeMaxParallel),
      regressionMaxParallel: parallel(s?.tests?.regressionMaxParallel),
      performanceMaxParallel: parallel(s?.tests?.performanceMaxParallel),
    }),
    testJobs: new FormArray((s?.testJobs ?? []).map(createTestJobForm)),
    deployment: new FormGroup({
      target: new FormControl<DeployTarget>(
        s?.deployment?.target ?? defaults?.deployTarget ?? 'VM',
        { nonNullable: true },
      ),
      appName: text(s?.deployment?.appName, max(200)),
      artifactName: text(s?.deployment?.artifactName, max(300)),
      baseArtifactName: text(s?.deployment?.baseArtifactName, max(300)),
    }),
    delivery: createToolCommandForm(s?.delivery),
    urbanCode: new FormGroup({
      siteName: text(s?.urbanCode?.siteName, max(200)),
      deployProcess: text(s?.urbanCode?.deployProcess, max(200)),
      skipWait: flag(s?.urbanCode?.skipWait),
      deployWithSnapshot: flag(s?.urbanCode?.deployWithSnapshot, true),
      updateSnapshotComponents: flag(s?.urbanCode?.updateSnapshotComponents),
      includeOnlyDeployVersions: flag(s?.urbanCode?.includeOnlyDeployVersions, true),
      deployOnlyChanged: flag(s?.urbanCode?.deployOnlyChanged),
      deployDescription: text(s?.urbanCode?.deployDescription, max(1000)),
      requestProperties: text(s?.urbanCode?.requestProperties, max(2000)),
    }),
    urbanCodeApplications: new FormArray(
      (s?.urbanCodeApplications ?? []).map(createUrbanCodeApplicationForm),
    ),
    sshTargets: new FormGroup({
      RD: createSshTargetForm(s?.sshTargets?.RD),
      QC: createSshTargetForm(s?.sshTargets?.QC),
    }),
    openShiftTargets: new FormGroup({
      RD: createOpenShiftTargetForm(s?.openShiftTargets?.RD),
      QC: createOpenShiftTargetForm(s?.openShiftTargets?.QC),
    }),
    appScan: new FormGroup({
      applicationId: text(s?.appScan?.applicationId, Validators.required, Validators.pattern(UUID)),
      sastScanName: text(s?.appScan?.sastScanName, max(200)),
      includedDirs: folders(s?.appScan?.includedDirs),
      excludedDirs: folders(s?.appScan?.excludedDirs),
      compile: flag(s?.appScan?.compile, true),
      sourceCodeOnly: flag(s?.appScan?.sourceCodeOnly),
      useConfigFile: flag(s?.appScan?.useConfigFile),
      insecureTls: flag(s?.appScan?.insecureTls),
      clientPath: text(s?.appScan?.clientPath, max(500)),
      compileCommand: createToolCommandForm(s?.appScan?.compileCommand),
      dastEnabled: flag(s?.appScan?.dastEnabled),
      dastScanName: text(s?.appScan?.dastScanName, max(200)),
      dastTargetUrl: text(s?.appScan?.dastTargetUrl, Validators.pattern(HTTP_URL), max(1000)),
      dastPresenceId: text(s?.appScan?.dastPresenceId, max(100)),
    }),
    sonar: new FormGroup({
      projectName: text(s?.sonar?.projectName, max(200)),
      projectKey: text(s?.sonar?.projectKey, Validators.pattern(SONAR_KEY), max(400)),
      installationName: text(s?.sonar?.installationName, max(200)),
      credentialsId: text(s?.sonar?.credentialsId, max(200)),
      authTokenCredentialsId: text(s?.sonar?.authTokenCredentialsId, max(200)),
      badgeToken: text(s?.sonar?.badgeToken, Validators.pattern(BADGE_TOKEN), max(200)),
      addBadges: flag(s?.sonar?.addBadges),
      fullBadges: flag(s?.sonar?.fullBadges),
      command: createToolCommandForm(s?.sonar?.command),
    }),
    nexusIq: new FormGroup({
      application: text(s?.nexusIq?.application, max(200)),
      scanPatterns: text(
        joinLines(s?.nexusIq?.scanPatterns),
        maxLines(20),
        eachItem(lines, upTo(300), 'At most 300 characters per pattern'),
      ),
      stage: text(s?.nexusIq?.stage ?? 'build', Validators.pattern(NEXUS_STAGE), max(50)),
      failOnNetworkError: flag(s?.nexusIq?.failOnNetworkError),
      scaScanName: text(s?.nexusIq?.scaScanName, max(200)),
    }),
    scm: new FormGroup({
      repositoryUrl: text(s?.scm?.repositoryUrl, Validators.pattern(HTTP_URL), max(1000)),
      credentialsId: text(s?.scm?.credentialsId, max(200)),
      authType: new FormControl<BitbucketAuthType>(s?.scm?.authType ?? 'BASIC', {
        nonNullable: true,
      }),
      type: new FormControl<BitbucketType | null>(s?.scm?.type ?? null),
      targetBranch: text(s?.scm?.targetBranch, max(200)),
      cloneUrl: text(s?.scm?.cloneUrl, Validators.pattern(CLONE_URL), max(1000)),
      reviewers: text(
        joinWords(s?.scm?.reviewers, ', '),
        maxWords(20),
        eachItem(words, upTo(100), 'At most 100 characters per reviewer'),
      ),
    }),
    goldenFix: createServiceGoldenFixForm(s?.goldenFix),
    metrics: new FormGroup({
      enabled: flag(s?.metrics?.enabled, true),
      influxProject: text(s?.metrics?.influxProject, Validators.pattern(METRICS_TAG), max(200)),
      influxEnv: text(s?.metrics?.influxEnv ?? 'test', Validators.pattern(METRICS_TAG), max(50)),
    }),
    flutter: new FormGroup({
      platform: new FormControl<FlutterPlatform | null>(s?.flutter?.platform ?? null),
      modules: modules(s?.flutter?.modules),
      testModules: modules(s?.flutter?.testModules),
      testSubmodules: modules(s?.flutter?.testSubmodules),
      testSubplugins: modules(s?.flutter?.testSubplugins),
      signingPasswordCredentialsId: text(
        s?.flutter?.signingPasswordCredentialsId,
        Validators.required,
        max(200),
      ),
      prodLicenseCredentialsId: text(
        s?.flutter?.prodLicenseCredentialsId,
        Validators.required,
        max(200),
      ),
      testLicenseCredentialsId: text(
        s?.flutter?.testLicenseCredentialsId,
        Validators.required,
        max(200),
      ),
      deliveryGroup: text(s?.flutter?.deliveryGroup, max(200)),
      deliveryArtifact: text(s?.flutter?.deliveryArtifact, max(200)),
      deliveryPlugin: text(s?.flutter?.deliveryPlugin, max(300)),
      sonarSources: text(s?.flutter?.sonarSources, max(500)),
      sonarTests: text(s?.flutter?.sonarTests, max(500)),
      sonarFlutterPlugin: flag(s?.flutter?.sonarFlutterPlugin),
      dartAnalyzeCommand: text(s?.flutter?.dartAnalyzeCommand, max(500)),
      sonarScannerVersion: text(
        s?.flutter?.sonarScannerVersion,
        Validators.pattern(SCANNER_VERSION),
        max(50),
      ),
    }),
  });

  const { build, deployment, appScan, unitTests, sonar, scm } = form.controls;
  const tool = build.controls.tool;
  const target = deployment.controls.target;
  const openShift = () => target.value === 'OPENSHIFT';
  build.controls.command.controls.tasks.addValidators(Validators.required);
  form.controls.delivery.controls.tasks.addValidators(Validators.required);
  requireWhile(
    build.controls.javaPath,
    () => tool.value !== 'FLUTTER' && !build.controls.autoSetup.value,
    tool,
    build.controls.autoSetup,
  );
  requireWhile(deployment.controls.appName, openShift, target);
  requireWhile(deployment.controls.artifactName, openShift, target);
  requireWhile(
    appScan.controls.dastTargetUrl,
    () => appScan.controls.dastEnabled.value,
    appScan.controls.dastEnabled,
  );
  requireWhile(
    unitTests.controls.command.controls.tasks,
    () => unitTestsConfigured(form),
    unitTests,
  );
  requireWhile(
    sonar.controls.command.controls.tasks,
    () => !!optional(sonar.controls.projectKey.value),
    sonar.controls.projectKey,
  );
  requireWhile(
    scm.controls.credentialsId,
    () => !!optional(scm.controls.repositoryUrl.value),
    scm.controls.repositoryUrl,
  );
  [build.controls.command.controls.tasks, form.controls.delivery.controls.tasks].forEach(
    (control) => control.updateValueAndValidity(),
  );
  tool.valueChanges.subscribe(() => syncApplicability(form));
  target.valueChanges.subscribe(() => syncApplicability(form));
  syncApplicability(form);
  return form;
}

export type ServiceForm = ReturnType<typeof createServiceForm>;

export function unitTestsConfigured(form: ServiceForm): boolean {
  const v = form.controls.unitTests.getRawValue();
  return (
    Object.values(v.command).some((value) => !!optional(value)) ||
    [v.resultPattern, v.rootDir, v.reportOutDir].some((value) => !!optional(value)) ||
    v.allowEmptyResults
  );
}

function syncApplicability(form: ServiceForm): void {
  const tool = form.controls.build.controls.tool.value;
  const vm = form.controls.deployment.controls.target.value === 'VM';
  const flutter = tool === 'FLUTTER';
  setEnabled(form.controls.build.controls.command, !flutter);
  setEnabled(form.controls.unitTests.controls.command, !flutter);
  setEnabled(form.controls.sonar.controls.command, !flutter);
  setEnabled(form.controls.appScan.controls.compileCommand, !flutter);
  setEnabled(form.controls.delivery, vm && tool === 'MAVEN');
  setEnabled(form.controls.urbanCode, vm);
  setEnabled(form.controls.urbanCodeApplications, vm);
  setEnabled(form.controls.sshTargets, vm);
  setEnabled(form.controls.openShiftTargets, !vm);
  setEnabled(form.controls.flutter, flutter);
}

export function createProductForm() {
  return new FormGroup({
    code: text('', Validators.required, Validators.pattern(PRODUCT_CODE)),
    name: text('', Validators.required, Validators.maxLength(200)),
    description: text('', Validators.maxLength(4000)),
    ownerTeam: text('', Validators.maxLength(200)),
    contactEmail: text('', Validators.email, Validators.maxLength(320)),
    appScan: new FormGroup({
      keyId: text('', Validators.required, Validators.maxLength(200)),
      secretCredentialsId: text('', Validators.maxLength(200)),
    }),
    services: new FormArray<ServiceForm>([]),
  });
}

export type ProductForm = ReturnType<typeof createProductForm>;

export function patchProduct(form: ProductForm, product: Product): void {
  form.patchValue({
    code: product.code,
    name: product.name,
    description: product.description ?? '',
    ownerTeam: product.ownerTeam ?? '',
    contactEmail: product.contactEmail ?? '',
    appScan: {
      keyId: product.appScan.keyId,
      secretCredentialsId: product.appScan.secretCredentialsId ?? '',
    },
  });
  form.controls.services.clear();
  product.services.forEach((service) => form.controls.services.push(createServiceForm(service)));
}

export function duplicateService(source: ServiceForm): ServiceForm {
  const copy = createServiceForm(toServiceRequest(source));
  copy.patchValue({
    id: null,
    name: `${source.controls.name.value}-copy`.slice(0, 100),
    sonar: { projectKey: '' },
    metrics: { influxProject: '' },
  });
  return copy;
}

export function toProductRequest(form: ProductForm, version: number | null): ProductRequest {
  const value = form.getRawValue();
  return {
    code: value.code.trim(),
    name: value.name.trim(),
    description: optional(value.description),
    ownerTeam: optional(value.ownerTeam),
    contactEmail: optional(value.contactEmail),
    appScan: {
      keyId: value.appScan.keyId.trim(),
      secretCredentialsId: optional(value.appScan.secretCredentialsId),
    },
    version,
    services: form.controls.services.controls.map(toServiceRequest),
  };
}

export function toServiceRequest(form: ServiceForm): ServiceRequest {
  const c = form.controls;
  const v = form.getRawValue();
  const tool = v.build.tool;
  const flutter = tool === 'FLUTTER';
  const vm = v.deployment.target === 'VM';
  const command = (group: ToolCommandForm, applies: boolean) =>
    applies ? toToolCommand(group) : { ...NO_COMMAND };
  return {
    id: v.id,
    name: v.name.trim(),
    description: optional(v.description),
    build: {
      tool,
      sourceDir: optional(v.build.sourceDir) ?? '.',
      javaPath: flutter ? null : optional(v.build.javaPath),
      autoSetup: v.build.autoSetup,
      buildPath: optional(v.build.buildPath),
      command: command(c.build.controls.command, !flutter),
    },
    unitTests: {
      command: command(c.unitTests.controls.command, !flutter),
      resultPattern: optional(v.unitTests.resultPattern),
      rootDir: optional(v.unitTests.rootDir),
      reportOutDir: optional(v.unitTests.reportOutDir),
      allowEmptyResults: v.unitTests.allowEmptyResults,
      coverageReportPath: optional(v.unitTests.coverageReportPath),
    },
    tests: { ...v.tests },
    testJobs: c.testJobs.controls.map(toTestJob),
    deployment: {
      target: v.deployment.target,
      appName: optional(v.deployment.appName),
      artifactName: optional(v.deployment.artifactName),
      baseArtifactName: optional(v.deployment.baseArtifactName),
    },
    delivery: command(c.delivery, vm && tool === 'MAVEN'),
    urbanCode: {
      ...v.urbanCode,
      siteName: optional(v.urbanCode.siteName),
      deployProcess: optional(v.urbanCode.deployProcess),
      deployDescription: optional(v.urbanCode.deployDescription),
      requestProperties: optional(v.urbanCode.requestProperties),
    },
    urbanCodeApplications: vm ? c.urbanCodeApplications.controls.map(toUrbanCodeApplication) : [],
    sshTargets: vm ? regionTargets(v.sshTargets, trimTarget) : {},
    openShiftTargets: vm ? {} : regionTargets(v.openShiftTargets, trimTarget),
    appScan: {
      ...v.appScan,
      applicationId: v.appScan.applicationId.trim(),
      sastScanName: optional(v.appScan.sastScanName),
      includedDirs: lines(v.appScan.includedDirs),
      excludedDirs: lines(v.appScan.excludedDirs),
      clientPath: optional(v.appScan.clientPath),
      compileCommand: command(c.appScan.controls.compileCommand, !flutter),
      dastScanName: optional(v.appScan.dastScanName),
      dastTargetUrl: optional(v.appScan.dastTargetUrl),
      dastPresenceId: optional(v.appScan.dastPresenceId),
    },
    sonar: {
      ...v.sonar,
      projectName: optional(v.sonar.projectName),
      projectKey: optional(v.sonar.projectKey),
      installationName: optional(v.sonar.installationName),
      credentialsId: optional(v.sonar.credentialsId),
      authTokenCredentialsId: optional(v.sonar.authTokenCredentialsId),
      badgeToken: optional(v.sonar.badgeToken),
      command: command(c.sonar.controls.command, !flutter),
    },
    nexusIq: {
      application: optional(v.nexusIq.application),
      scanPatterns: lines(v.nexusIq.scanPatterns),
      stage: optional(v.nexusIq.stage) ?? 'build',
      failOnNetworkError: v.nexusIq.failOnNetworkError,
      scaScanName: optional(v.nexusIq.scaScanName),
    },
    scm: {
      repositoryUrl: optional(v.scm.repositoryUrl),
      credentialsId: optional(v.scm.credentialsId),
      authType: v.scm.authType,
      type: v.scm.type,
      targetBranch: optional(v.scm.targetBranch),
      cloneUrl: optional(v.scm.cloneUrl),
      reviewers: words(v.scm.reviewers),
    },
    goldenFix: v.goldenFix.inherit
      ? inheritedGoldenFix(v.goldenFix.enabled)
      : toGoldenFixPolicy(v.goldenFix),
    metrics: {
      enabled: v.metrics.enabled,
      influxProject: optional(v.metrics.influxProject),
      influxEnv: optional(v.metrics.influxEnv),
    },
    flutter: flutter ? toFlutterSettings(v.flutter) : null,
  };
}

function toFlutterSettings(
  v: ReturnType<ServiceForm['controls']['flutter']['getRawValue']>,
): FlutterSettings {
  return {
    platform: v.platform,
    modules: lines(v.modules),
    testModules: lines(v.testModules),
    testSubmodules: lines(v.testSubmodules),
    testSubplugins: lines(v.testSubplugins),
    signingPasswordCredentialsId: optional(v.signingPasswordCredentialsId),
    prodLicenseCredentialsId: optional(v.prodLicenseCredentialsId),
    testLicenseCredentialsId: optional(v.testLicenseCredentialsId),
    deliveryGroup: optional(v.deliveryGroup),
    deliveryArtifact: optional(v.deliveryArtifact),
    deliveryPlugin: optional(v.deliveryPlugin),
    sonarSources: optional(v.sonarSources),
    sonarTests: optional(v.sonarTests),
    sonarFlutterPlugin: v.sonarFlutterPlugin,
    dartAnalyzeCommand: optional(v.dartAnalyzeCommand),
    sonarScannerVersion: optional(v.sonarScannerVersion),
  };
}

function trimTarget<T extends object>(value: T): Trimmed<T> {
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      typeof item === 'string' ? optional(item) : item,
    ]),
  ) as Trimmed<T>;
}

type Trimmed<T> = { [K in keyof T]: T[K] extends string ? string | null : T[K] };

function regionTargets<T extends object, R>(
  targets: Record<Region, T>,
  convert: (target: T) => R,
): Partial<Record<Region, R>> {
  const result: Partial<Record<Region, R>> = {};
  for (const region of ['RD', 'QC'] as Region[]) {
    const target = convert(targets[region]);
    if (Object.values(target as object).some((value) => value !== null && value !== false)) {
      result[region] = target;
    }
  }
  return result;
}

export function firstServiceWithProblem(problems: FieldProblem[]): number | null {
  const indexes = problems
    .map((problem) => /^services\[(\d+)]/.exec(problem.field))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => Number(match[1]));
  return indexes.length ? Math.min(...indexes) : null;
}

export type ServiceSectionId =
  | 'general'
  | 'build'
  | 'unitTests'
  | 'testJobs'
  | 'deployment'
  | 'urbanCode'
  | 'ssh'
  | 'openShift'
  | 'appScan'
  | 'sonar'
  | 'nexusIq'
  | 'scm'
  | 'goldenFix'
  | 'metrics'
  | 'flutter';

type ServiceControlKey = keyof ServiceForm['controls'];

export interface ServiceSection {
  id: ServiceSectionId;
  label: string;
  icon: string;
  keys: ServiceControlKey[];
  applies?: (tool: BuildTool, target: DeployTarget) => boolean;
}

export const SERVICE_SECTIONS: ServiceSection[] = [
  { id: 'general', label: 'General', icon: 'badge', keys: ['name', 'description'] },
  { id: 'build', label: 'Build', icon: 'build', keys: ['build'] },
  { id: 'unitTests', label: 'Unit tests and coverage', icon: 'fact_check', keys: ['unitTests'] },
  { id: 'testJobs', label: 'Test jobs', icon: 'science', keys: ['tests', 'testJobs'] },
  {
    id: 'deployment',
    label: 'Deployment',
    icon: 'rocket_launch',
    keys: ['deployment', 'delivery'],
  },
  {
    id: 'urbanCode',
    label: 'UrbanCode Deploy',
    icon: 'hub',
    keys: ['urbanCode', 'urbanCodeApplications'],
    applies: (_, target) => target === 'VM',
  },
  {
    id: 'ssh',
    label: 'SSH targets',
    icon: 'dns',
    keys: ['sshTargets'],
    applies: (_, target) => target === 'VM',
  },
  {
    id: 'openShift',
    label: 'OpenShift targets',
    icon: 'cloud',
    keys: ['openShiftTargets'],
    applies: (_, target) => target === 'OPENSHIFT',
  },
  { id: 'appScan', label: 'AppScan SAST and DAST', icon: 'security', keys: ['appScan'] },
  { id: 'sonar', label: 'SonarQube', icon: 'analytics', keys: ['sonar'] },
  { id: 'nexusIq', label: 'Nexus IQ', icon: 'inventory', keys: ['nexusIq'] },
  { id: 'scm', label: 'Bitbucket', icon: 'merge', keys: ['scm'] },
  { id: 'goldenFix', label: 'GoldenFix', icon: 'auto_fix_high', keys: ['goldenFix'] },
  { id: 'metrics', label: 'DORA metrics', icon: 'insights', keys: ['metrics'] },
  {
    id: 'flutter',
    label: 'Flutter',
    icon: 'phone_iphone',
    keys: ['flutter'],
    applies: (tool) => tool === 'FLUTTER',
  },
];

export function visibleSections(form: ServiceForm): ServiceSection[] {
  const tool = form.controls.build.controls.tool.value;
  const target = form.controls.deployment.controls.target.value;
  return SERVICE_SECTIONS.filter((section) => section.applies?.(tool, target) ?? true);
}

export function sectionInvalid(form: ServiceForm, section: ServiceSection): boolean {
  return section.keys.some((key) => form.controls[key].invalid);
}

export function sectionTouched(form: ServiceForm, section: ServiceSection): boolean {
  return section.keys.some((key) => form.controls[key].touched);
}

export function firstInvalidSection(form: ServiceForm): ServiceSectionId | null {
  return visibleSections(form).find((section) => sectionInvalid(form, section))?.id ?? null;
}
