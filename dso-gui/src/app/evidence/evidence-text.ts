import {
  CheckStatus,
  EvidenceScanner,
  GoldenFixEvidence,
  PipelineEvidence,
  ProductEvidence,
  RunEvidence,
  ScanEvidence,
  ServiceEvidence,
  StageEvidence,
  TEST_SUITES,
  TestSuite,
  TestSuiteEvidence,
  pipelineTypeLabel,
} from '../core/models';
import { CHECK_LOOK } from '../shared/check-chip';
import { counted, durationOrNull, formatDuration } from '@common/shared/formatting';
import { RUN_LOOK } from '../shared/status-chip';

const NOT_RECORDED = 'Not recorded';

export const SUITE_LABELS: Record<TestSuite, string> = {
  UNIT: 'Unit',
  SMOKE: 'Smoke',
  REGRESSION: 'Regression',
  PERFORMANCE: 'Performance',
};

const EVIDENCE_SCANNERS: { scanner: EvidenceScanner; label: string }[] = [
  { scanner: 'SAST', label: 'SAST (HCL AppScan)' },
  { scanner: 'DAST', label: 'DAST (HCL AppScan)' },
  { scanner: 'SONARQUBE', label: 'SonarQube' },
  { scanner: 'NEXUS_IQ', label: 'Nexus IQ' },
];

const GOLDEN_FIX_RESULTS: Record<string, string> = {
  PR_CREATED: 'Pull request raised',
  PR_UPDATED: 'Pull request updated',
  NO_FIXES: 'No safe versions offered',
  NO_MANIFEST_CHANGES: 'Nothing to change',
  NOT_CONFIGURED: 'No Bitbucket repository set',
  BUILD_FAILED: 'Upgrades do not build',
  SKIPPED: 'Skipped',
  ERROR: 'Failed',
};

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
): { suite: TestSuite; evidence: TestSuiteEvidence | null }[] {
  return TEST_SUITES.map((suite) => ({
    suite,
    evidence: run.testSuites.find((found) => found.suite === suite) ?? null,
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

export function goldenFixResult(fix: GoldenFixEvidence): string {
  return GOLDEN_FIX_RESULTS[fix.status] ?? fix.status;
}

export function goldenFixUpgrades(fix: GoldenFixEvidence): string {
  return `${fix.applied} of ${counted(fix.offered, 'upgrade')} applied, ${fix.unresolved} unresolved`;
}

export function evidenceText(
  product: ProductEvidence,
  service: ServiceEvidence,
  pipeline: PipelineEvidence,
): string {
  const lines = [
    `DevSecOps change evidence: ${product.name} (${product.code}), ${service.name}, ${pipelineTypeLabel(pipeline.type)} pipeline`,
    '',
    ...(product.metricsError
      ? [
          `Warning: the run results could not be read (${product.metricsError}), so the run, tests, scans and release gate below may show as Not recorded although they were recorded. Copy the evidence again once they can be read.`,
          '',
        ]
      : []),
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
    field('Status', RUN_LOOK[pipeline.status]?.label ?? pipeline.status),
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
    field('Result', RUN_LOOK[build.result]?.label ?? build.result),
    field('Finished', formatUtc(build.finishedAt)),
    field('Branch', build.branch),
    field('Commit', build.commit),
    field('Artifact version', build.artifactVersion),
    field('Duration', durationOrNull(build.durationSeconds)),
    field('Job', build.job),
    field('Build link', build.url),
    field('Pipeline report', build.reportUrl),
    field('Unit test report', build.testReportUrl),
    field('Artifacts', build.artifactsUrl),
    field('Config rendered', formatUtc(build.configRenderedAt)),
    field('Config sha256', build.configSha256),
    '',
    'Unit test coverage',
    coverageLine(run),
    '',
    'Tests',
    ...suiteRows(run).map(({ suite, evidence }) =>
      field(SUITE_LABELS[suite], evidence && suiteText(evidence)),
    ),
    '',
    'Security and quality scans',
    ...scanRows(run).map(({ label, scan }) => field(label, scan && scanText(scan))),
    ...goldenFixLines(run.goldenFix),
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
    lines.push(field(stage.name, stageDetails(stage).join(', ')));
  }
  return lines.join('\n') + '\n';
}

export function stageDetails(stage: StageEvidence): string[] {
  return [checkLabel(stage.status), durationOrNull(stage.durationSeconds), stage.reason].filter(
    (part): part is string => !!part,
  );
}

function checkLabel(status: CheckStatus): string {
  return CHECK_LOOK[status]?.label ?? status;
}

function field(label: string, value: string | null | undefined): string {
  return `- ${label}: ${value === null || value === undefined || value === '' ? NOT_RECORDED : value}`;
}

function coverageLine(run: RunEvidence): string {
  const coverage = run.coverage;
  if (!coverage || coverage.status === 'NO_DATA') {
    return field('Line coverage', null);
  }
  const parts = [checkLabel(coverage.status)];
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
  const parts = [checkLabel(suite.status)];
  if (suite.total !== null) {
    const unit = suite.suite === 'UNIT' ? 'tests' : 'jobs';
    parts.push(`${suite.passed ?? 0} of ${suite.total} ${unit} passed`);
  }
  if (suite.failed) {
    parts.push(`${suite.failed} failed`);
  }
  if (suite.skipped) {
    parts.push(`${suite.skipped} skipped`);
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
  const parts = [checkLabel(scan.status)];
  if (scan.scanner === 'SONARQUBE' && (scan.qualityGate || !hasFindings(scan))) {
    parts.push(scan.qualityGate ? `quality gate ${scan.qualityGate}` : 'quality gate');
  }
  if (hasFindings(scan)) {
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

function goldenFixLines(fix: GoldenFixEvidence | null): string[] {
  if (!fix) {
    return [];
  }
  const pullRequest = [fix.pullRequestTitle, fix.pullRequestUrl].filter(Boolean).join(', ');
  return [
    '',
    'GoldenFix',
    field('Result', goldenFixResult(fix)),
    field('Upgrades', goldenFixUpgrades(fix)),
    field('Pull request', pullRequest || (fix.pullRequestRaised ? null : 'None')),
  ];
}

function gateText(run: RunEvidence): string | null {
  const gate = run.releaseGate;
  if (!gate) {
    return null;
  }
  if (gate.allowed) {
    return 'Release allowed';
  }
  const violations = gate.violations === null ? '' : `, ${counted(gate.violations, 'violation')}`;
  return `Release blocked${violations}${gate.reason ? `: ${gate.reason}` : ''}`;
}
