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
import { createGlobalGoldenFixForm, toGlobalGoldenFixPolicy } from '../products/product-form-model';
import {
  HOST_NAME,
  HTTP_URL,
  flag,
  integer,
  optional,
  requireWhile,
  requiredRule,
  sent,
  text,
} from '../shared/form-controls';

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
      coverageMinLine: integer(s?.coverageMinLine, 1, 100, required),
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
        validators: requiredRule('Select at least one scanner'),
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
    goldenFix: createGlobalGoldenFixForm(settings?.goldenFix),
  });
  const { proxyHost, proxyPort } = form.controls.platform.controls;
  requireWhile(proxyPort, () => !!optional(proxyHost.value), proxyHost);
  requireWhile(proxyHost, () => proxyPort.value !== null, proxyPort);
  return form;
}

export type SettingsForm = ReturnType<typeof createSettingsForm>;
export type SettingsSectionId = keyof SettingsForm['controls'];

export function patchSettings(form: SettingsForm, settings: GlobalSettingsValues): void {
  form.reset(createSettingsForm(settings).getRawValue());
}

export function toSettingsRequest(
  form: SettingsForm,
  version: number | null,
): GlobalSettingsRequest {
  const v = form.getRawValue();
  const limits = Object.fromEntries(
    SCANNERS.map((scanner) => [scanner, sent(v.limits[scanner])]),
  ) as Record<Scanner, SeverityLimits>;
  return {
    version,
    platform: sent(v.platform),
    deployment: sent(v.deployment),
    limits,
    scans: sent(v.scans),
    releaseGate: {
      ...sent(v.releaseGate),
      scanners: SCANNERS.filter((scanner) => v.releaseGate.scanners.includes(scanner)),
    },
    serviceDefaults: {
      ...sent(v.serviceDefaults),
      sourceDir: optional(v.serviceDefaults.sourceDir) ?? '.',
    },
    goldenFix: toGlobalGoldenFixPolicy(form.controls.goldenFix),
  };
}

export const SETTINGS_SECTIONS: { id: SettingsSectionId; label: string }[] = [
  { id: 'platform', label: 'Platform and tools' },
  { id: 'deployment', label: 'Deployment defaults' },
  { id: 'limits', label: 'Severity limits' },
  { id: 'scans', label: 'Scans and coverage' },
  { id: 'releaseGate', label: 'Release gate' },
  { id: 'serviceDefaults', label: 'Service defaults' },
  { id: 'goldenFix', label: 'GoldenFix defaults' },
];

export function firstInvalidSection(form: SettingsForm): SettingsSectionId | null {
  return SETTINGS_SECTIONS.find((section) => form.controls[section.id].invalid)?.id ?? null;
}
