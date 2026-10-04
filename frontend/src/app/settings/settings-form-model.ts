import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import {
  BuildTool,
  DEFAULT_JENKINS_LIBRARY,
  DeployTarget,
  GlobalSettingsRequest,
  GlobalSettingsValues,
  SCANNERS,
  Scanner,
  SeverityLimits,
} from '../core/models';
import { goldenFixControls, toGoldenFixPolicy } from '../products/product-form-model';
import {
  HOST_NAME,
  HTTP_URL,
  flag,
  integer,
  optional,
  requireWhile,
  text,
} from '../shared/form-controls';

/**
 * The reactive form of the global settings. Its shape follows the API request, so a field problem the API
 * reports (for example {@code platform.proxyPort} or {@code limits[SAST].maxHigh}) points at its control.
 */

export const STATE_FILE = /^[A-Za-z0-9._-]*$/;

const required = Validators.required;
const max = (length: number) => Validators.maxLength(length);
const url = (value: string | null | undefined, length: number, ...validators: ValidatorFn[]) =>
  text(value, ...validators, Validators.pattern(HTTP_URL), max(length));
const host = (value: string | null | undefined, ...validators: ValidatorFn[]) =>
  text(value, ...validators, Validators.pattern(HOST_NAME), max(255));

function createLimitsForm(limits?: SeverityLimits) {
  const count = (value: number | undefined) => integer(value, 0, 100_000, required);
  return new FormGroup({
    maxCritical: count(limits?.maxCritical),
    maxHigh: count(limits?.maxHigh),
    maxMedium: count(limits?.maxMedium),
  });
}

export function createSettingsForm(settings?: GlobalSettingsValues | null) {
  const p = settings?.platform;
  const d = settings?.deployment;
  const s = settings?.scans;
  const minutes = (value: number | undefined) => integer(value, 1, 1440, required);
  const seconds = (value: number | undefined) => integer(value, 1, 3600, required);
  const form = new FormGroup({
    platform: new FormGroup({
      jenkinsUrl: url(p?.jenkinsUrl, 500),
      jenkinsLibrary: text(p?.jenkinsLibrary ?? DEFAULT_JENKINS_LIBRARY, required, max(200)),
      asocUrl: url(p?.asocUrl, 500, required),
      appScanClientLinuxUrl: url(p?.appScanClientLinuxUrl, 1000, required),
      appScanClientWindowsUrl: url(p?.appScanClientWindowsUrl, 1000, required),
      proxyHost: host(p?.proxyHost),
      proxyPort: integer(p?.proxyPort, 1, 65535),
      proxyUser: text(p?.proxyUser, max(100)),
      oisHost: host(p?.oisHost),
      sonarServerUrl: url(p?.sonarServerUrl, 500, required),
      sonarInstallationName: text(p?.sonarInstallationName, required, max(200)),
      nexusIqServerUrl: url(p?.nexusIqServerUrl, 500, required),
      nexusIqCredentialsId: text(p?.nexusIqCredentialsId, required, max(200)),
      nexusSnapshotRepositoryUrl: url(p?.nexusSnapshotRepositoryUrl, 1000),
      nexusSnapshotRepositoryId: text(p?.nexusSnapshotRepositoryId, max(200)),
      influxWriteUrl: url(p?.influxWriteUrl, 1000),
      influxCredentialsId: text(p?.influxCredentialsId, max(200)),
      iosBuildAgent: text(p?.iosBuildAgent, max(255)),
    }),
    deployment: new FormGroup({
      urbanCodeSiteName: text(d?.urbanCodeSiteName, required, max(200)),
      urbanCodeDeployProcess: text(d?.urbanCodeDeployProcess, required, max(200)),
      rdHost: host(d?.rdHost, required),
      qcHost: host(d?.qcHost, required),
      sshUser: text(d?.sshUser, required, max(100)),
      deployScript: text(d?.deployScript, required, max(500)),
      versionFile: text(d?.versionFile, required, max(500)),
    }),
    limits: new FormGroup({
      SAST: createLimitsForm(settings?.limits.SAST),
      SCA: createLimitsForm(settings?.limits.SCA),
      NEXUS_IQ: createLimitsForm(settings?.limits.NEXUS_IQ),
      DAST: createLimitsForm(settings?.limits.DAST),
    }),
    scans: new FormGroup({
      coverageMinLine: integer(s?.coverageMinLine, 0, 100, required),
      sastPrepareTimeoutMinutes: minutes(s?.sastPrepareTimeoutMinutes),
      sastPollTimeoutMinutes: minutes(s?.sastPollTimeoutMinutes),
      sastPollIntervalSeconds: seconds(s?.sastPollIntervalSeconds),
      scaEnabled: flag(s?.scaEnabled, true),
      scaPollTimeoutMinutes: minutes(s?.scaPollTimeoutMinutes),
      scaPollIntervalSeconds: seconds(s?.scaPollIntervalSeconds),
      dastPollTimeoutMinutes: minutes(s?.dastPollTimeoutMinutes),
      dastPollIntervalSeconds: seconds(s?.dastPollIntervalSeconds),
      dastReportTimeoutMinutes: minutes(s?.dastReportTimeoutMinutes),
      dastReportIntervalSeconds: seconds(s?.dastReportIntervalSeconds),
      sonarWaitForQualityGate: flag(s?.sonarWaitForQualityGate, true),
      sonarQualityGateTimeoutMinutes: minutes(s?.sonarQualityGateTimeoutMinutes),
    }),
    releaseGate: new FormGroup({
      scanners: new FormControl<Scanner[]>(settings?.releaseGate.scanners ?? [...SCANNERS], {
        nonNullable: true,
      }),
      requireCoverage: flag(settings?.releaseGate.requireCoverage, true),
      stateFile: text(
        settings?.releaseGate.stateFile ?? 'release-gate.json',
        required,
        Validators.pattern(STATE_FILE),
        max(200),
      ),
    }),
    serviceDefaults: new FormGroup({
      buildTool: new FormControl<BuildTool>(settings?.serviceDefaults.buildTool ?? 'GRADLE', {
        nonNullable: true,
      }),
      deployTarget: new FormControl<DeployTarget>(settings?.serviceDefaults.deployTarget ?? 'VM', {
        nonNullable: true,
      }),
      sourceDir: text(settings?.serviceDefaults.sourceDir ?? '.', max(500)),
      testsMaxParallel: integer(settings?.serviceDefaults.testsMaxParallel, 1, 100, required),
    }),
    goldenFix: new FormGroup(goldenFixControls(settings?.goldenFix, true)),
  });
  // The AppScan calls go through the proxy only when both its host and its port are known.
  const { proxyHost, proxyPort } = form.controls.platform.controls;
  requireWhile(proxyPort, () => !!optional(proxyHost.value), proxyHost);
  requireWhile(proxyHost, () => proxyPort.value !== null, proxyPort);
  return form;
}

export type SettingsForm = ReturnType<typeof createSettingsForm>;
export type SettingsSectionId = keyof SettingsForm['controls'];

/** Fills the form with stored settings and marks it as unchanged. */
export function patchSettings(form: SettingsForm, settings: GlobalSettingsValues): void {
  form.reset(createSettingsForm(settings).getRawValue());
}

/** The request for the settings; the form is valid, so every required number is set. */
export function toSettingsRequest(form: SettingsForm, version: number | null): GlobalSettingsRequest {
  const v = form.getRawValue();
  const number = (value: number | null) => value as number;
  const limits = Object.fromEntries(
    SCANNERS.map((scanner) => [
      scanner,
      {
        maxCritical: number(v.limits[scanner].maxCritical),
        maxHigh: number(v.limits[scanner].maxHigh),
        maxMedium: number(v.limits[scanner].maxMedium),
      },
    ]),
  ) as Record<Scanner, SeverityLimits>;
  return {
    version,
    platform: {
      jenkinsUrl: optional(v.platform.jenkinsUrl),
      jenkinsLibrary: v.platform.jenkinsLibrary.trim(),
      asocUrl: v.platform.asocUrl.trim(),
      appScanClientLinuxUrl: v.platform.appScanClientLinuxUrl.trim(),
      appScanClientWindowsUrl: v.platform.appScanClientWindowsUrl.trim(),
      proxyHost: optional(v.platform.proxyHost),
      proxyPort: v.platform.proxyPort,
      proxyUser: optional(v.platform.proxyUser),
      oisHost: optional(v.platform.oisHost),
      sonarServerUrl: v.platform.sonarServerUrl.trim(),
      sonarInstallationName: v.platform.sonarInstallationName.trim(),
      nexusIqServerUrl: v.platform.nexusIqServerUrl.trim(),
      nexusIqCredentialsId: v.platform.nexusIqCredentialsId.trim(),
      nexusSnapshotRepositoryUrl: optional(v.platform.nexusSnapshotRepositoryUrl),
      nexusSnapshotRepositoryId: optional(v.platform.nexusSnapshotRepositoryId),
      influxWriteUrl: optional(v.platform.influxWriteUrl),
      influxCredentialsId: optional(v.platform.influxCredentialsId),
      iosBuildAgent: optional(v.platform.iosBuildAgent),
    },
    deployment: {
      urbanCodeSiteName: v.deployment.urbanCodeSiteName.trim(),
      urbanCodeDeployProcess: v.deployment.urbanCodeDeployProcess.trim(),
      rdHost: v.deployment.rdHost.trim(),
      qcHost: v.deployment.qcHost.trim(),
      sshUser: v.deployment.sshUser.trim(),
      deployScript: v.deployment.deployScript.trim(),
      versionFile: v.deployment.versionFile.trim(),
    },
    limits,
    scans: {
      coverageMinLine: number(v.scans.coverageMinLine),
      sastPrepareTimeoutMinutes: number(v.scans.sastPrepareTimeoutMinutes),
      sastPollTimeoutMinutes: number(v.scans.sastPollTimeoutMinutes),
      sastPollIntervalSeconds: number(v.scans.sastPollIntervalSeconds),
      scaEnabled: v.scans.scaEnabled,
      scaPollTimeoutMinutes: number(v.scans.scaPollTimeoutMinutes),
      scaPollIntervalSeconds: number(v.scans.scaPollIntervalSeconds),
      dastPollTimeoutMinutes: number(v.scans.dastPollTimeoutMinutes),
      dastPollIntervalSeconds: number(v.scans.dastPollIntervalSeconds),
      dastReportTimeoutMinutes: number(v.scans.dastReportTimeoutMinutes),
      dastReportIntervalSeconds: number(v.scans.dastReportIntervalSeconds),
      sonarWaitForQualityGate: v.scans.sonarWaitForQualityGate,
      sonarQualityGateTimeoutMinutes: number(v.scans.sonarQualityGateTimeoutMinutes),
    },
    releaseGate: {
      scanners: SCANNERS.filter((scanner) => v.releaseGate.scanners.includes(scanner)),
      requireCoverage: v.releaseGate.requireCoverage,
      stateFile: v.releaseGate.stateFile.trim(),
    },
    serviceDefaults: {
      buildTool: v.serviceDefaults.buildTool,
      deployTarget: v.serviceDefaults.deployTarget,
      sourceDir: optional(v.serviceDefaults.sourceDir) ?? '.',
      testsMaxParallel: number(v.serviceDefaults.testsMaxParallel),
    },
    goldenFix: toGoldenFixPolicy(v.goldenFix),
  };
}

/** The sections of the settings page, in the order they appear. */
export const SETTINGS_SECTIONS: { id: SettingsSectionId; label: string; icon: string }[] = [
  { id: 'platform', label: 'Platform and tools', icon: 'hub' },
  { id: 'deployment', label: 'Deployment defaults', icon: 'dns' },
  { id: 'limits', label: 'Severity limits', icon: 'policy' },
  { id: 'scans', label: 'Scans and coverage', icon: 'timer' },
  { id: 'releaseGate', label: 'Release gate', icon: 'verified' },
  { id: 'serviceDefaults', label: 'Service defaults', icon: 'tune' },
  { id: 'goldenFix', label: 'GoldenFix defaults', icon: 'auto_fix_high' },
];

/** The first section holding an invalid value, or null when the form is valid. */
export function firstInvalidSection(form: SettingsForm): SettingsSectionId | null {
  return SETTINGS_SECTIONS.find((section) => form.controls[section.id].invalid)?.id ?? null;
}
