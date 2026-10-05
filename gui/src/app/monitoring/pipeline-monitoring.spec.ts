import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { PipelineMonitoring } from '../core/models';
import {
  doraSummary,
  monitoringPipeline,
  pipelineMonitoring,
  pipelineRun,
} from '../testing/fixtures';
import { PipelineMonitoringPage } from './pipeline-monitoring';

describe('PipelineMonitoringPage', () => {
  let fixture: ComponentFixture<PipelineMonitoringPage>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PipelineMonitoringPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(PipelineMonitoringPage);
    fixture.componentRef.setInput('id', '100');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const text = (selector: string) => page().querySelector(selector)?.textContent?.trim();

  async function load(data: PipelineMonitoring = pipelineMonitoring(), range = '30d') {
    fixture.detectChanges();
    http.expectOne(`/api/monitoring/pipelines/100?range=${range}`).flush(data);
    await fixture.whenStable();
  }

  it('shows the pipeline with its DORA tiles, latest run and Grafana panels', async () => {
    await load();

    expect(text('h1')).toContain('gui');
    expect(text('h1')).toContain('Full pipeline');
    expect([...page().querySelectorAll('.tile-title')].map((t) => t.textContent)).toEqual([
      'Deployment frequency',
      'Lead time for changes',
      'Change failure rate',
      'Time to restore',
    ]);
    expect(text('.last-run')).toContain('10 passed · 1 warned · 1 failed · 0 blocked · 0 skipped');
    expect(page().querySelectorAll('.panels iframe').length).toBe(1);
    expect(page().querySelector('a.jenkins')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
    );
    expect(text('.page')).not.toContain('The key of this pipeline is invalidated');
  });

  it('links every run to the build address the API sent for it', async () => {
    await load();

    expect(page().querySelector('.last-run a.build-link')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    );
    const builds = [...page().querySelectorAll('td.mat-column-build')];
    expect(builds[0].querySelector('a')?.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    );
    expect(builds[1].querySelector('a')).toBeNull();
    expect(builds[1].textContent?.trim()).toBe('#41');
  });

  it('shows a dash for a run without a build number', async () => {
    await load(
      pipelineMonitoring({
        lastRun: pipelineRun({ build: null, buildUrl: null, stagesTotal: null }),
        recentRuns: [pipelineRun({ build: null, buildUrl: null, stagesTotal: null })],
      }),
    );

    expect(page().querySelector('a.build-link')).toBeNull();
    expect(text('td.mat-column-build')).toBe('–');
    expect(text('td.mat-column-stages')).toBe('–');
  });

  it('warns that a pipeline without an active key stops at start-up', async () => {
    await load(pipelineMonitoring({ pipeline: monitoringPipeline({ activeKey: null }) }));

    expect(text('.page')).toContain('The key of this pipeline is invalidated');
  });

  it('explains what is missing without runs, without Grafana and without metrics', async () => {
    await load(
      pipelineMonitoring({
        lastRun: null,
        recentRuns: [],
        grafana: null,
        metricsError: 'InfluxDB timed out',
        pipeline: monitoringPipeline({ jenkinsJobUrl: null }),
      }),
    );

    expect(text('.last-run')).toContain('No run reported yet');
    expect(text('.page')).toContain('No runs in this range.');
    expect(text('.grafana')).toContain('Grafana is not configured');
    expect(text('.page')).toContain('InfluxDB timed out');
    expect(page().querySelector('a.jenkins')).toBeNull();
  });

  it('keeps an unknown range at 30 days and switches the range in the address', async () => {
    fixture.componentRef.setInput('range', '5y');
    await load();

    page().querySelectorAll<HTMLButtonElement>('mat-button-toggle button')[2].click();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { range: '90d' }, replaceUrl: true }),
    );
  });

  it('reads the range from the address', async () => {
    fixture.componentRef.setInput('range', '7d');
    await load(pipelineMonitoring(), '7d');

    expect(text('.mat-button-toggle-checked')).toBe('7d');
  });

  it('shows why the pipeline could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush({ detail: 'Pipeline 100 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text('.banner')).toBe('Pipeline 100 was not found');
    expect(text('a[mat-stroked-button]')).toBe('Back to monitoring');
  });

  it('reads the metrics again on refresh and shows the progress meanwhile', async () => {
    await load();
    const refresh = page().querySelector<HTMLButtonElement>(
      'button[aria-label="Refresh the pipeline metrics"]',
    )!;
    expect(page().querySelector('mat-progress-bar')).toBeNull();

    refresh.click();
    TestBed.tick();

    expect(page().querySelector('mat-progress-bar')).not.toBeNull();
    expect(refresh.disabled).toBe(true);
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(pipelineMonitoring({ dora: doraSummary({ deployments: 13 }) }));
    await fixture.whenStable();

    expect(page().querySelector('mat-progress-bar')).toBeNull();
    expect(refresh.disabled).toBe(false);
  });

  it('warns on the time to restore tile since when the pipeline is failing', async () => {
    await load(pipelineMonitoring({ dora: doraSummary({ failingSince: '2026-10-04T08:00:00Z' }) }));

    const alerts = page().querySelectorAll('.tile-alert');
    expect(alerts.length).toBe(1);
    expect(alerts[0].textContent).toMatch(/^Failing since 4 Oct, \d\d:00$/);
  });
});
