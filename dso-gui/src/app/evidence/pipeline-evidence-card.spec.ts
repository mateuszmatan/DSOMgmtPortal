import { Clipboard } from '@angular/cdk/clipboard';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PipelineEvidence, RunEvidence } from '../core/models';
import { toast } from '@common/testing/dom';
import {
  goldenFixEvidence,
  pipelineEvidence,
  productEvidence,
  runEvidence,
  serviceEvidence,
} from '../testing/fixtures';
import { PipelineEvidenceCard } from './pipeline-evidence-card';

describe('PipelineEvidenceCard', () => {
  let fixture: ComponentFixture<PipelineEvidenceCard>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PipelineEvidenceCard] });
    fixture = TestBed.createComponent(PipelineEvidenceCard);
  });

  const card = () => fixture.nativeElement as HTMLElement;
  const text = (selector: string) =>
    card().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const facts = () =>
    Object.fromEntries(
      [...card().querySelectorAll('dl dt')].map((term) => [
        term.textContent?.trim(),
        term.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim(),
      ]),
    );
  const row = (label: string) =>
    [...card().querySelectorAll<HTMLTableRowElement>('tbody tr')].find(
      (tr) => tr.cells[0].textContent?.trim() === label,
    )!;
  const cells = (label: string) =>
    [...row(label).cells].slice(1).map((cell) => cell.textContent?.replace(/\s+/g, ' ').trim());

  async function render(pipeline: PipelineEvidence = pipelineEvidence()) {
    const service = serviceEvidence({ pipelines: [pipeline] });
    fixture.componentRef.setInput('product', productEvidence({ services: [service] }));
    fixture.componentRef.setInput('service', service);
    fixture.componentRef.setInput('pipeline', pipeline);
    await fixture.whenStable();
  }

  const withRun = (run: Partial<RunEvidence>) =>
    pipelineEvidence({ run: { ...runEvidence(), ...run } });

  it('shows a complete run: build facts, coverage against the requirement and every suite', async () => {
    await render();

    expect(text('h4')).toBe('Full pipeline');
    expect(card().querySelector('.pipeline')?.classList).not.toContain('inactive');
    expect(card().querySelector<HTMLAnchorElement>('.actions a')?.href).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
    );
    expect(facts()).toMatchObject({
      Finished: '2026-10-04 08:30 UTC',
      Branch: 'release/2.4',
      Commit: '9f2c1e7b4d3a',
      'Artifact version': '2.4.0-42',
      Duration: '22m 5s',
      'Settings sent': '2026-10-04 07:55 UTC',
      'Settings fingerprint': '3b7e1f0a9c2d4e5f',
    });
    expect(text('.actions a')).toBe('Open in Jenkins');
    expect(text('.stages-head h5')).toBe('Stages of the latest build');
    expect(text('.stage-counts')).toBe('2 passed, 1 failed, 1 blocked');
    expect([...card().querySelectorAll('.grid h5')].map((h) => h.textContent)).toEqual([
      'Release gate',
      'Unit test coverage',
      'Jenkins build',
    ]);
    expect(text('.coverage .value')).toBe('84.25%');
    expect(text('p.detail')).toBe('Required 60% · 1685 of 2000 lines');
    expect(card().querySelector<HTMLElement>('.meter .fill')?.style.width).toBe('84.25%');
    expect(card().querySelector<HTMLElement>('.meter .required')?.style.left).toBe('60%');
    expect(cells('Unit')).toEqual(['Passed', '412', '410', '0', '2', '0', '4m']);
    expect(cells('Smoke')).toEqual(['Passed', '2', '2', '0', '–', '0', '1m 35s']);
    expect(row('Regression').querySelector('td.bad')?.textContent?.trim()).toBe('1');
    expect(cells('Performance')).toEqual(['Not recorded']);
    expect(card().querySelector('.golden-fix')).toBeNull();
  });

  it('says what GoldenFix did and links its pull request', async () => {
    await render(withRun({ goldenFix: goldenFixEvidence() }));

    expect(text('.golden-fix h5')).toBe('Golden pull request (GoldenFix)');
    expect(text('.golden-fix p')).toBe(
      'Pull request raised · 2 of 3 upgrades applied, 1 unresolved · GoldenFix-202610040815',
    );
    const link = card().querySelector<HTMLAnchorElement>('.golden-fix a');
    expect(link?.getAttribute('href')).toBe(
      'https://bitbucket.bbh.com/projects/CERT/repos/gui/pull-requests/17',
    );
    expect(link?.target).toBe('_blank');

    await render(withRun({ goldenFix: goldenFixEvidence({ pullRequestTitle: null }) }));
    expect(text('.golden-fix a')).toBe('Pull request');

    await render(
      withRun({
        goldenFix: goldenFixEvidence({
          status: 'NO_FIXES',
          offered: 0,
          applied: 0,
          unresolved: 2,
          pullRequestRaised: false,
          pullRequestUrl: null,
          pullRequestTitle: null,
        }),
      }),
    );
    expect(text('.golden-fix p')).toBe(
      'No safe versions offered · 0 of 0 upgrades applied, 2 unresolved',
    );
    expect(card().querySelector('.golden-fix a')).toBeNull();
  });

  it('reads the scans against the limits of the policy', async () => {
    await render();

    expect(cells('SAST (HCL AppScan)')).toEqual([
      'Passed',
      '0 / 0',
      '0 / 0',
      '3 / 5',
      '12',
      'Open',
    ]);
    expect(cells('DAST (HCL AppScan)')).toEqual(['Not recorded']);
    expect(cells('SonarQube · gate OK')).toEqual(['Passed', 'Quality gate only', 'Open']);
    expect(row('SAST (HCL AppScan)').querySelector('a')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/42/artifact/appscan/sast-report.html',
    );
    expect(
      [...row('Nexus IQ').querySelectorAll('td.bad')].map((cell) => cell.textContent?.trim()),
    ).toEqual(['1 / 0']);
  });

  it('marks every value a partial run does not hold as not recorded', async () => {
    await render(
      pipelineEvidence({
        enabled: false,
        jenkinsJobUrl: null,
        run: runEvidence({
          build: {
            ...runEvidence().build,
            number: null,
            finishedAt: null,
            branch: null,
            commit: null,
            artifactVersion: null,
            durationSeconds: null,
            url: null,
            reportUrl: null,
            testReportUrl: null,
            artifactsUrl: null,
            configRenderedAt: null,
            configSha256: null,
          },
          coverage: null,
          releaseGate: null,
          testSuites: [],
          scans: [],
          stages: [],
        }),
      }),
    );

    expect(card().querySelector('.pipeline')?.classList).toContain('inactive');
    expect(text('.actions .not-recorded')).toBe('No Jenkins job');
    expect(card().querySelector('dl dd .not-recorded')?.textContent).toBe('Not recorded');
    expect(facts()).toMatchObject({
      Finished: 'Not recorded',
      Branch: 'Not recorded',
      Commit: 'Not recorded',
      'Artifact version': 'Not recorded',
      Duration: 'Not recorded',
      'Settings sent': 'Not recorded',
      'Settings fingerprint': 'Not recorded',
    });
    expect([...card().querySelectorAll('.links span')].map((link) => link.textContent)).toEqual([
      'Build: not recorded',
      'Pipeline report: not recorded',
      'Unit test report: not recorded',
      'Artifacts: not recorded',
    ]);
    expect(text('.stages')).toBe('No stages recorded');
    expect(
      [...card().querySelectorAll('.grid section > p.not-recorded')].map((p) => p.textContent),
    ).toEqual(['Not recorded', 'Not recorded']);
    expect(cells('Unit')).toEqual(['Not recorded']);
    expect(cells('Smoke')).toEqual(['Not recorded']);
    expect(cells('Nexus IQ')).toEqual(['Not recorded']);
  });

  it('states the release gate decision with its violations', async () => {
    await render(withRun({ releaseGate: { allowed: false, violations: 2, reason: null } }));
    expect(text('.gate')).toBe('Release blocked · 2 violations');

    await render(withRun({ releaseGate: { allowed: false, violations: null, reason: 'No scan' } }));
    expect(text('.gate strong')).toBe('Release blocked');
    expect(text('.gate .detail')).toBe('No scan');

    await render(withRun({ releaseGate: { allowed: true, violations: 0, reason: null } }));
    expect(text('.gate')).toBe('Release allowed');
    expect(card().querySelector('.gate')?.classList).toContain('allowed');
  });

  it('says when the evidence could not be copied', async () => {
    vi.spyOn(TestBed.inject(Clipboard), 'copy').mockReturnValue(false);
    await render();

    card().querySelector<HTMLButtonElement>('.actions button')!.click();
    await fixture.whenStable();

    expect(toast()?.classList).toContain('error');
    expect(toast()?.textContent).toContain('The evidence could not be copied to the clipboard.');
  });

  it('confirms a copied evidence text', async () => {
    vi.spyOn(TestBed.inject(Clipboard), 'copy').mockReturnValue(true);
    await render();

    card().querySelector<HTMLButtonElement>('.actions button')!.click();
    await fixture.whenStable();

    expect(toast()?.textContent).toContain(
      'Evidence of gui · Full pipeline copied. Paste it into the ProTech change.',
    );
  });
});
