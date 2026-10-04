import {
  CheckStatus,
  EvidenceScanner,
  PIPELINE_TYPES,
  PipelineEvidence,
  PipelineType,
  ProductEvidence,
  RunEvidence,
  RunResult,
  ScanEvidence,
  ServiceEvidence,
  TEST_STAGES,
  TestStage,
  TestSuiteEvidence,
} from '../core/models';
import { formatDuration } from '../shared/formatting';

export const NOT_RECORDED = 'Not recorded';

export const CHECK_LABELS: Record<CheckStatus, string> = {
  PASS: 'Passed',
  WARN: 'Warning',
  FAIL: 'Failed',
  BLOCKED: 'Blocked',
  NOT_REQUIRED: 'Not required',
  SKIP: 'Skipped',
  NO_DATA: NOT_RECORDED,
};

export const RUN_LABELS: Record<RunResult, string> = {
  SUCCESS: 'Success',
  UNSTABLE: 'Unstable',
  FAILURE: 'Failed',
  ABORTED: 'Aborted',
  NOT_BUILT: 'Not built',
  NO_DATA: 'No runs yet',
  DISABLED: 'Key invalidated',
};

export const STAGE_LABELS: Record<TestStage, string> = {
  SMOKE: 'Smoke',
  REGRESSION: 'Regression',
  PERFORMANCE: 'Performance',
};

export const EVIDENCE_SCANNERS: { scanner: EvidenceScanner; label: string }[] = [
  { scanner: 'SAST', label: 'SAST (HCL AppScan)' },
  { scanner: 'DAST', label: 'DAST (HCL AppScan)' },
  { scanner: 'SONARQUBE', label: 'SonarQube' },
  { scanner: 'NEXUS_IQ', label: 'Nexus IQ' },
];

export function pipelineTypeLabel(type: PipelineType): string {
  return PIPELINE_TYPES.find((option) => option.value === type)?.label ?? type;
}

export function formatUtc(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? null
    : `${date.toISOString().slice(0, 16).replace('T', ' ')} UTC`;
}

export function formatPercent(value: number | null | undefined): string | null {
  return value === null || value === undefined
    ? null
    : `${Number.isInteger(value) ? value : value.toFixed(2)}%`;
}

export function suiteRows(
  run: RunEvidence,
): { stage: TestStage; suite: TestSuiteEvidence | null }[] {
  return TEST_STAGES.map((stage) => ({
    stage,
    suite: run.testSuites.find((suite) => suite.stage === stage) ?? null,
  }));
}

export function scanRows(
  run: RunEvidence,
): { scanner: EvidenceScanner; label: string; scan: ScanEvidence | null }[] {
  return EVIDENCE_SCANNERS.map(({ scanner, label }) => ({
    scanner,
    label,
    scan: run.scans.find((scan) => scan.scanner === scanner) ?? null,
  }));
}

export function hasFindings(scan: ScanEvidence): boolean {
  return [scan.critical, scan.high, scan.medium, scan.low].some((value) => value !== null);
}

export function evidenceText(
  product: ProductEvidence,
  service: ServiceEvidence,
  pipeline: PipelineEvidence,
): string {
  const lines = [
    `DevSecOps change evidence: ${product.name} (${product.code}), ${service.name}, ${pipelineTypeLabel(pipeline.type)} pipeline`,
    '',
    'Product',
    field('Name', `${product.name} (${product.code})`),
    field('Owner team', product.ownerTeam),
    field('Contact', product.contactEmail),
    '',
    'Service',
    field('Name', service.name),
    field('Repository', service.repositoryUrl),
    field('Artifact', service.artifactName),
    field('HCL AppScan application ID', service.appScanApplicationId),
    field('SonarQube project key', service.sonarProjectKey),
    field('Nexus IQ application', service.nexusIqApplication),
    '',
    'Pipeline',
    field('Type', `${pipelineTypeLabel(pipeline.type)} (${pipeline.type})`),
    field('Status', RUN_LABELS[pipeline.status] ?? pipeline.status),
    field('Jenkins job', pipeline.jenkinsJobUrl),
  ];
  if (!pipeline.enabled) {
    lines.push(field('Key', 'Invalidated'));
  }
  const run = pipeline.run;
  if (!run) {
    lines.push('', field('Latest run', NOT_RECORDED));
    return lines.join('\n') + '\n';
  }
  const build = run.build;
  lines.push(
    '',
    'Jenkins build',
    field('Build', build.number === null ? null : `#${build.number}`),
    field('Result', RUN_LABELS[build.result] ?? build.result),
    field('Finished', formatUtc(build.finishedAt)),
    field('Branch', build.branch),
    field('Commit', build.commit),
    field(
      'Duration',
      build.durationSeconds === null ? null : formatDuration(build.durationSeconds),
    ),
    field('Job', build.job),
    field('Build link', build.url),
    field('Pipeline report', build.reportUrl),
    field('Unit test report', build.testReportUrl),
    field('Artifacts', build.artifactsUrl),
    '',
    'Unit test coverage',
    coverageLine(run),
    '',
    'Tests',
    ...suiteRows(run).map(({ stage, suite }) =>
      field(STAGE_LABELS[stage], suite && suiteText(suite)),
    ),
    '',
    'Security and quality scans',
    ...scanRows(run).map(({ label, scan }) => field(label, scan && scanText(scan))),
    '',
    'Release gate',
    field('Decision', gateText(run)),
    '',
    'Stages',
  );
  if (run.stages.length === 0) {
    lines.push(`- ${NOT_RECORDED}`);
  }
  for (const stage of run.stages) {
    const details = [
      CHECK_LABELS[stage.status] ?? stage.status,
      stage.durationSeconds === null ? null : formatDuration(stage.durationSeconds),
      stage.reason,
    ].filter((part): part is string => !!part);
    lines.push(field(stage.name, details.join(', ')));
  }
  return lines.join('\n') + '\n';
}

function field(label: string, value: string | null | undefined): string {
  return `- ${label}: ${value === null || value === undefined || value === '' ? NOT_RECORDED : value}`;
}

function coverageLine(run: RunEvidence): string {
  const coverage = run.coverage;
  if (!coverage || coverage.status === 'NO_DATA') {
    return field('Line coverage', null);
  }
  const parts = [CHECK_LABELS[coverage.status] ?? coverage.status];
  const percent = formatPercent(coverage.linePercent);
  if (percent) {
    const lines =
      coverage.coveredLines !== null && coverage.totalLines !== null
        ? ` (${coverage.coveredLines} of ${coverage.totalLines} lines)`
        : '';
    parts.push(`${percent} of lines covered${lines}`);
  }
  const required = formatPercent(coverage.requiredPercent);
  parts.push(required ? `required ${required}` : `required ${NOT_RECORDED.toLowerCase()}`);
  return field('Line coverage', parts.join(', '));
}

function suiteText(suite: TestSuiteEvidence): string {
  const parts = [CHECK_LABELS[suite.status] ?? suite.status];
  if (suite.jobs !== null) {
    parts.push(`${suite.passed ?? 0} of ${suite.jobs} jobs passed`);
  }
  if (suite.failed) {
    parts.push(`${suite.failed} failed`);
  }
  if (suite.notConfigured) {
    parts.push(`${suite.notConfigured} not configured`);
  }
  if (suite.durationMs !== null) {
    parts.push(formatDuration(suite.durationMs / 1000));
  }
  return parts.join(', ');
}

function scanText(scan: ScanEvidence): string {
  const parts = [CHECK_LABELS[scan.status] ?? scan.status];
  if (scan.scanner === 'SONARQUBE' && !hasFindings(scan)) {
    parts.push('quality gate');
  } else if (hasFindings(scan)) {
    parts.push(
      [
        severity('critical', scan.critical, scan.maxCritical),
        severity('high', scan.high, scan.maxHigh),
        severity('medium', scan.medium, scan.maxMedium),
        severity('low', scan.low, null),
      ].join(', '),
    );
  }
  if (scan.link) {
    parts.push(scan.link);
  }
  return parts.join(', ');
}

function severity(label: string, value: number | null, max: number | null): string {
  const count = value === null ? NOT_RECORDED.toLowerCase() : String(value);
  return max === null ? `${label} ${count}` : `${label} ${count} (limit ${max})`;
}

function gateText(run: RunEvidence): string | null {
  const gate = run.releaseGate;
  if (!gate) {
    return null;
  }
  if (gate.allowed) {
    return 'Release allowed';
  }
  const violations =
    gate.violations === null
      ? ''
      : `, ${gate.violations} ${gate.violations === 1 ? 'violation' : 'violations'}`;
  return `Release blocked${violations}${gate.reason ? `: ${gate.reason}` : ''}`;
}
