import {
  pipelineEvidence,
  productEvidence,
  runEvidence,
  serviceEvidence,
} from '../testing/fixtures';
import {
  evidenceText,
  formatPercent,
  formatUtc,
  hasFindings,
  scanRows,
  suiteRows,
} from './evidence-text';

const JOB = 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/';
const BUILD = `${JOB}42/`;

describe('evidenceText', () => {
  it('writes the evidence of a run in the order a ServiceNow change asks for', () => {
    const product = productEvidence();
    const service = product.services[0];

    expect(evidenceText(product, service, service.pipelines[0])).toBe(
      [
        'DevSecOps change evidence: CertScanner (CERT), gui, Full pipeline',
        '',
        'Product',
        '- Name: CertScanner (CERT)',
        '- Owner team: Technology Architecture',
        '- Contact: arch@bbh.com',
        '',
        'Service',
        '- Name: gui',
        '- Repository: https://bitbucket.bbh.com/projects/CERT/repos/gui',
        '- Artifact: cert-gui.jar',
        '- HCL AppScan application ID: 109f44ac-cc06-4ca0-884e-d944904f7019',
        '- SonarQube project key: cert-gui',
        '- Nexus IQ application: Not recorded',
        '',
        'Pipeline',
        '- Type: Full (FULL)',
        '- Status: Success',
        `- Jenkins job: ${JOB}`,
        '',
        'Jenkins build',
        '- Build: #42',
        '- Result: Success',
        '- Finished: 2026-10-04 08:30 UTC',
        '- Branch: release/2.4',
        '- Commit: 9f2c1e7b4d3a5f6e7d8c9b0a1f2e3d4c5b6a7980',
        '- Duration: 22m 5s',
        '- Job: DevSecOps/CERT/gui-full',
        `- Build link: ${BUILD}`,
        `- Pipeline report: ${BUILD}Pipeline_20Report/`,
        `- Unit test report: ${BUILD}testReport/`,
        `- Artifacts: ${BUILD}artifact/`,
        '',
        'Unit test coverage',
        '- Line coverage: Passed, 84.25% of lines covered (1685 of 2000 lines), required 60%',
        '',
        'Tests',
        '- Smoke: Passed, 2 of 2 jobs passed, 1m 35s',
        '- Regression: Warning, 2 of 3 jobs passed, 1 failed, 30m',
        '- Performance: Not recorded',
        '',
        'Security and quality scans',
        `- SAST (HCL AppScan): Passed, critical 0 (limit 0), high 0 (limit 0), medium 3 (limit 5), low 12, ${BUILD}`,
        '- DAST (HCL AppScan): Not recorded',
        '- SonarQube: Passed, quality gate, https://tools.bbh.com/sonar/dashboard?id=cert-gui',
        `- Nexus IQ: Failed, critical 1 (limit 0), high 2 (limit 2), medium 0 (limit 10), low 0, ${BUILD}`,
        '',
        'Release gate',
        '- Decision: Release blocked, 1 violation: Nexus IQ: 1 critical above the limit of 0',
        '',
        'Stages',
        '- Build: Passed, 2m 5s',
        '- Unit tests: Passed, 4m',
        '- Nexus IQ: Failed, 1m, 1 critical finding',
        '- Deploy QC: Blocked, Release gate',
        '',
      ].join('\n'),
    );
  });

  it('says a pipeline without a recorded run has none, and an invalidated key', () => {
    const product = productEvidence();
    const pipeline = pipelineEvidence({
      type: 'SAST',
      enabled: false,
      jenkinsJobUrl: null,
      status: 'DISABLED',
      run: null,
    });

    const text = evidenceText(product, serviceEvidence({ pipelines: [pipeline] }), pipeline);

    expect(text).toContain(
      'DevSecOps change evidence: CertScanner (CERT), gui, SAST scanning pipeline\n',
    );
    expect(text).toContain(
      '- Status: Key invalidated\n- Jenkins job: Not recorded\n- Key: Invalidated\n',
    );
    expect(text.endsWith('\n- Latest run: Not recorded\n')).toBe(true);
    expect(text).not.toContain('Jenkins build');
  });

  it('writes Not recorded for every value the run left out', () => {
    const run = runEvidence({
      build: {
        number: null,
        finishedAt: null,
        result: 'NO_DATA',
        branch: null,
        commit: null,
        durationSeconds: null,
        job: null,
        url: null,
        reportUrl: null,
        testReportUrl: null,
        artifactsUrl: null,
      },
      coverage: null,
      testSuites: [],
      scans: [],
      releaseGate: null,
      stages: [],
    });
    const product = productEvidence({ ownerTeam: null, contactEmail: null });
    const service = serviceEvidence({ repositoryUrl: null, artifactName: '' });

    const text = evidenceText(product, service, pipelineEvidence({ run }));

    for (const line of [
      '- Owner team: Not recorded',
      '- Contact: Not recorded',
      '- Repository: Not recorded',
      '- Artifact: Not recorded',
      '- Build: Not recorded',
      '- Result: No runs yet',
      '- Finished: Not recorded',
      '- Duration: Not recorded',
      '- Build link: Not recorded',
      '- Line coverage: Not recorded',
      '- Smoke: Not recorded',
      '- Nexus IQ: Not recorded',
      '- Decision: Not recorded',
      'Stages\n- Not recorded',
    ]) {
      expect(text).toContain(line);
    }
  });

  it('describes partial coverage and an allowed release', () => {
    const run = runEvidence({
      coverage: {
        status: 'FAIL',
        linePercent: 41,
        requiredPercent: null,
        coveredLines: null,
        totalLines: 900,
      },
      releaseGate: { allowed: true, violations: 0, reason: null },
    });

    const text = evidenceText(productEvidence(), serviceEvidence(), pipelineEvidence({ run }));

    expect(text).toContain(
      '- Line coverage: Failed, 41% of lines covered, required not recorded\n',
    );
    expect(text).toContain('- Decision: Release allowed\n');
  });

  it('marks scan counts the run did not record', () => {
    const run = runEvidence({
      scans: [
        {
          scanner: 'DAST',
          status: 'WARN',
          critical: 0,
          high: null,
          medium: 4,
          low: null,
          maxCritical: 0,
          maxHigh: 1,
          maxMedium: null,
          link: null,
        },
      ],
    });

    const text = evidenceText(productEvidence(), serviceEvidence(), pipelineEvidence({ run }));

    expect(text).toContain(
      '- DAST (HCL AppScan): Warning, critical 0 (limit 0), high not recorded (limit 1), medium 4, low not recorded\n',
    );
    expect(text).toContain('- SAST (HCL AppScan): Not recorded\n');
  });
});

describe('evidence helpers', () => {
  it('tells a scan with counts from one reporting its gate only', () => {
    const [sast, sonar] = runEvidence().scans;
    expect(hasFindings(sast)).toBe(true);
    expect(hasFindings(sonar)).toBe(false);
  });
});
