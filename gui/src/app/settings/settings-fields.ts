import {
  Field,
  GRADLE_MAVEN_FLUTTER,
  VM_OPENSHIFT,
  check,
  choice,
  count,
  line,
  mono,
} from '../shared/fields';
import { SHELL_SAFE_ERROR } from '../shared/form-controls';
import { SettingsSectionId } from './settings-form-model';

export interface SettingsBlock {
  heading: string;
  note: string;
  fields: Field[];
}

export interface SettingsSection {
  id: SettingsSectionId;
  label: string;
  note: string;
  fields?: Field[];
  blocks?: SettingsBlock[];
}

const HOST_ERROR = 'Use letters, digits, dots and hyphens';

const minutes = (key: string, label: string, code: string, span = 4): Field =>
  count(key, label, code, span, { min: 1 });

export const SETTINGS_PAGE: SettingsSection[] = [
  {
    id: 'platform',
    label: 'Platform and tools',
    note:
      'The BBH tool servers every pipeline talks to, sent as `platform` and as the tool settings ' +
      'of every service. Credentials are Jenkins credential IDs, never secrets.',
    blocks: [
      {
        heading: 'Jenkins',
        note: 'Where the pipelines run and the shared library their Jenkinsfiles load.',
        fields: [
          mono('jenkinsUrl', 'Jenkins URL', 'platform.jenkinsUrl', 7, {
            placeholder: 'https://jenkins.bbh.com/',
            hint: 'links the Jenkins job of each pipeline',
          }),
          mono('jenkinsLibrary', 'Shared library', 'platform.jenkinsLibrary', 5, {
            hint: 'the `@Library` name',
          }),
        ],
      },
      {
        heading: 'HCL AppScan',
        note: 'The AppScan on Cloud service, the static analysis clients and the proxy its calls go through.',
        fields: [
          mono('asocUrl', 'AppScan on Cloud URL', 'asoc.url', 12, {
            placeholder: 'https://cloud.appscan.com',
            hint: '`APPSCAN_SERVER_URL`',
          }),
          mono('appScanClientLinuxUrl', 'Static analysis client for Linux', 'SA_LINUX_URL', 6),
          mono('appScanClientWindowsUrl', 'Static analysis client for Windows', 'SA_WIN_URL', 6),
          mono('proxyHost', 'Proxy host', 'PROXY_HOST', 5, {
            hint: 'set together with the port',
            error: HOST_ERROR,
          }),
          {
            key: 'proxyPort',
            label: 'Proxy port',
            code: 'PROXY_PORT',
            span: 3,
            kind: 'number',
            min: 1,
            max: 65535,
          },
          mono('proxyUser', 'Proxy user', 'PROXY_USER', 4),
          mono('oisHost', 'OIS host', 'platform.oisHost', 6, { error: HOST_ERROR }),
        ],
      },
      {
        heading: 'SonarQube and Nexus IQ',
        note: 'The quality and dependency policy servers every service reports to.',
        fields: [
          mono('sonarServerUrl', 'SonarQube server URL', 'tools.sonar.serverUrl', 7),
          mono(
            'sonarInstallationName',
            'SonarQube installation',
            'tools.sonar.installationName',
            5,
          ),
          mono('nexusIqServerUrl', 'Nexus IQ server URL', 'tools.nexusIq.serverUrl', 7),
          mono('nexusIqCredentialsId', 'Nexus IQ credentials ID', 'tools.nexusIq.credentialsId', 5),
        ],
      },
      {
        heading: 'Repository, metrics and agents',
        note: 'The Nexus snapshot repository, the InfluxDB the pipelines write their metrics to and the agent iOS builds run on.',
        fields: [
          mono(
            'nexusSnapshotRepositoryUrl',
            'Nexus snapshot repository URL',
            'platform.nexusSnapshotRepositoryUrl',
            7,
          ),
          mono(
            'nexusSnapshotRepositoryId',
            'Nexus snapshot repository ID',
            'platform.nexusSnapshotRepositoryId',
            5,
          ),
          mono('influxWriteUrl', 'InfluxDB write URL', 'influx.url', 7),
          mono('influxCredentialsId', 'InfluxDB credentials ID', 'influx.credentialsId', 5),
          mono('iosBuildAgent', 'iOS build agent', 'platform.iosBuildAgent', 6, {
            hint: 'Jenkins agent label',
          }),
        ],
      },
    ],
  },
  {
    id: 'deployment',
    label: 'Deployment defaults',
    note: 'What a service deployed to virtual machines uses unless it sets its own value under `deploy.vm`.',
    blocks: [
      {
        heading: 'UrbanCode Deploy',
        note: 'The site and process UrbanCode deployments run with.',
        fields: [
          mono('urbanCodeSiteName', 'Site name', 'deploy.vm.dod.siteName', 6),
          mono('urbanCodeDeployProcess', 'Deploy process', 'deploy.vm.dod.deployProcess', 6),
        ],
      },
      {
        heading: 'SSH deployment',
        note: 'The test region hosts and how the deployment script is run on them.',
        fields: [
          mono('rdHost', 'RD host', 'deploy.vm.rd.host', 6, {
            hint: 'the lower test region',
            error: HOST_ERROR,
          }),
          mono('qcHost', 'QC host', 'deploy.vm.qc.host', 6, {
            hint: 'the higher test region',
            error: HOST_ERROR,
          }),
          mono('sshUser', 'SSH user', 'deploy.vm.<region>.user', 4, { error: SHELL_SAFE_ERROR }),
          mono('deployScript', 'Deploy script', 'deploy.vm.<region>.deployScript', 8, {
            error: SHELL_SAFE_ERROR,
          }),
          mono('versionFile', 'Version file', 'deploy.vm.<region>.versionFile', 12, {
            error: SHELL_SAFE_ERROR,
          }),
        ],
      },
    ],
  },
  {
    id: 'limits',
    label: 'Severity limits',
    note: 'The most findings of each severity a scan may report before it fails the pipeline. Zero allows none.',
  },
  {
    id: 'scans',
    label: 'Scans and coverage',
    note: 'The required unit test coverage and how long the pipelines wait for each scan.',
    blocks: [
      {
        heading: 'Coverage',
        note: 'The line coverage the unit tests must reach.',
        fields: [
          count('coverageMinLine', 'Minimum line coverage (%)', 'coverage.minLine', 4, {
            min: 1,
            max: 100,
            hint: '1 to 100',
          }),
        ],
      },
      {
        heading: 'SAST',
        note: 'Preparing the sources and waiting for the AppScan static scan.',
        fields: [
          minutes(
            'sastPrepareTimeoutMinutes',
            'Prepare timeout (minutes)',
            'sast.prepareTimeoutMin',
          ),
          minutes('sastPollTimeoutMinutes', 'Poll timeout (minutes)', 'sast.pollTimeoutMin'),
          minutes('sastPollIntervalSeconds', 'Poll interval (seconds)', 'sast.pollIntervalSec'),
        ],
      },
      {
        heading: 'SCA',
        note: 'The AppScan open source analysis of the dependencies.',
        fields: [
          {
            key: 'scaEnabled',
            label: 'Run the SCA scan',
            code: 'sca.enabled',
            span: 4,
            kind: 'check',
          },
          minutes('scaPollTimeoutMinutes', 'Poll timeout (minutes)', 'sca.pollTimeoutMin'),
          minutes('scaPollIntervalSeconds', 'Poll interval (seconds)', 'sca.pollIntervalSec'),
        ],
      },
      {
        heading: 'DAST',
        note: 'The AppScan dynamic scan of the deployed service and its report.',
        fields: [
          minutes('dastPollTimeoutMinutes', 'Poll timeout (minutes)', 'dast.pollTimeoutMin', 3),
          minutes('dastPollIntervalSeconds', 'Poll interval (seconds)', 'dast.pollIntervalSec', 3),
          minutes(
            'dastReportTimeoutMinutes',
            'Report timeout (minutes)',
            'dast.reportTimeoutMin',
            3,
          ),
          count(
            'dastReportIntervalSeconds',
            'Report interval (seconds)',
            'dast.reportIntervalSec',
            3,
          ),
        ],
      },
      {
        heading: 'SonarQube quality gate',
        note: 'Whether the pipelines wait for the SonarQube quality gate and for how long.',
        fields: [
          {
            key: 'sonarWaitForQualityGate',
            label: 'Wait for the quality gate',
            code: 'tools.sonar.qualityGate.waitForQualityGate',
            span: 6,
            kind: 'check',
          },
          count(
            'sonarQualityGateTimeoutMinutes',
            'Timeout (minutes)',
            'tools.sonar.qualityGate.timeoutMinutes',
            6,
          ),
        ],
      },
    ],
  },
  {
    id: 'releaseGate',
    label: 'Release gate',
    note:
      'What a run must prove before the pipeline may release: the scans whose limits must hold ' +
      'and, optionally, the required coverage.',
  },
  {
    id: 'serviceDefaults',
    label: 'Service defaults',
    note:
      'A new service starts with these values, and a service that leaves one of them out gets it ' +
      'from here. Each service may set its own.',
    fields: [
      choice('buildTool', 'Build tool', GRADLE_MAVEN_FLUTTER, 'buildTool', 3),
      choice('deployTarget', 'Deployment target', VM_OPENSHIFT, 'deployTarget', 3),
      mono('sourceDir', 'Source folder', 'sourceDir', 3, { placeholder: '.' }),
      count('testsMaxParallel', 'Parallel test jobs', 'tests.maxParallel', 3, { min: 1 }),
    ],
  },
  {
    id: 'goldenFix',
    label: 'GoldenFix defaults',
    note:
      'How GoldenFix raises dependency upgrade pull requests. A service follows this policy ' +
      'unless it overrides values in its own GoldenFix section.',
  },
];

export const LIMIT_FIELDS: Field[] = [
  count('maxCritical', 'Max critical', '', 0, { min: 0 }),
  count('maxHigh', 'Max high', '', 0, { min: 0 }),
  count('maxMedium', 'Max medium', '', 0, { min: 0 }),
];

export const RELEASE_GATE_FIELDS: Field[] = [
  check('requireCoverage', 'Require the minimum coverage', 'releaseGate.requireCoverage', 6),
  mono('stateFile', 'State file', 'releaseGate.stateFile', 6, {
    hint: 'archived with each build',
    error: 'Use a file name such as release-gate.json',
  }),
];

export const GOLDEN_FIX_ENABLED: Field[] = [
  check('enabled', 'GoldenFix runs by default', 'goldenFix.enabled'),
];
