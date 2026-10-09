import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Pipeline, PipelineMonitoring } from '../core/models';
import { PipelineDialog } from '../products/pipeline-dialog';
import { RevokeKeyDialog } from '../products/revoke-key-dialog';
import { CodeDialog } from '../shared/code-dialog';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { buttonOf, text } from '../testing/dom';
import { globalSettings, pipeline, pipelineMonitoring, pipelineRun } from '../testing/fixtures';
import { PipelinePage } from './pipeline-page';

const spyOnOpen = () => vi.spyOn(TestBed.inject(MatDialog), 'open');

describe('PipelinePage', () => {
  let fixture: ComponentFixture<PipelinePage>;
  let http: HttpTestingController;
  let router: Router;
  let open: ReturnType<typeof spyOnOpen>;

  const closed = (result: unknown) =>
    ({ afterClosed: () => of(result) }) as unknown as MatDialogRef<unknown>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PipelinePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    open = spyOnOpen().mockReturnValue(closed(undefined));
    fixture = TestBed.createComponent(PipelinePage);
    fixture.componentRef.setInput('id', '100');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const opened = (index = 0) => ({
    component: open.mock.calls[index][0],
    data: open.mock.calls[index][1]?.data as Record<string, unknown>,
  });

  async function load(
    stored: Pipeline = pipeline({ keys: [pipeline().activeKey!] }),
    monitoring: PipelineMonitoring = pipelineMonitoring(),
  ) {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/pipelines/100').flush(stored);
    http.expectOne('/api/monitoring/pipelines/100?range=30d').flush(monitoring);
    await fixture.whenStable();
  }

  async function menu(label: string) {
    buttonOf(page(), 'More').click();
    await fixture.whenStable();
    buttonOf(document, label).click();
    await fixture.whenStable();
  }

  it('shows the pipeline with its key, settings, Jenkinsfile and recent runs', async () => {
    await load();

    expect(text(page().querySelector('.breadcrumb'))).toBe(
      'DevSecOps Pipelines/CertScanner/gui · Full',
    );
    expect(text(page().querySelector('h1'))).toBe('gui · Full pipeline');
    expect(text(page().querySelector('.title dso-status-chip'))).toBe('Success');
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e…9abc');
    expect([...page().querySelectorAll('.pairs dt')].map(text).slice(0, 5)).toEqual([
      'Issued',
      'Last fetched over REST',
      'Earlier keys',
      'Agents',
      'Jenkins job',
    ]);
    expect(text(page().querySelector('pre.code-block'))).toBe(
      "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')",
    );
    expect(page().querySelectorAll('tbody tr').length).toBe(2);
    expect(
      [...page().querySelectorAll<HTMLAnchorElement>('.page-header .actions a')].map((link) =>
        link.getAttribute('href'),
      ),
    ).toEqual([
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
      '/monitoring/pipelines/100',
      '/admin/products/1',
    ]);
    expect(page().querySelector('.banner.danger')).toBeNull();
  });

  it('reveals and hides the key', async () => {
    await load();

    buttonOf(page(), 'Show').click();
    await fixture.whenStable();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e-0000-4abc-9def-123456789abc');

    buttonOf(page(), 'Hide').click();
    await fixture.whenStable();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e…9abc');
  });

  it('shows at most five runs and says when none was reported', async () => {
    const runs = Array.from({ length: 7 }, (_, index) => pipelineRun({ build: 40 + index }));
    await load(undefined, pipelineMonitoring({ recentRuns: runs }));
    expect(page().querySelectorAll('tbody tr').length).toBe(5);

    fixture.componentRef.setInput('id', '101');
    fixture.detectChanges();
    http.expectOne('/api/pipelines/101').flush(pipeline({ id: 101 }));
    http
      .expectOne('/api/monitoring/pipelines/101?range=30d')
      .flush(pipelineMonitoring({ recentRuns: [], metricsError: 'InfluxDB timed out' }));
    await fixture.whenStable();

    expect(text(page().querySelector('.small-empty'))).toBe('No run reported in the last 30 days.');
    expect(text(page().querySelector('dso-metrics-banner'))).toContain('InfluxDB timed out');
  });

  it('offers to regenerate an invalidated key and reveals the new one', async () => {
    await load(pipeline({ activeKey: null }));

    expect(text(page().querySelector('.banner.danger'))).toContain('The key is invalidated');
    expect(text(page().querySelector('.panel .muted'))).toBe('No active key.');
    expect(text(page().querySelector('pre.code-block'))).toContain('<issue a new key first>');

    buttonOf(page(), 'Regenerate key').click();
    await fixture.whenStable();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(pipeline());
    await fixture.whenStable();

    expect(page().querySelector('.banner.danger')).toBeNull();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e-0000-4abc-9def-123456789abc');
  });

  it('saves the settings in the pipeline dialog', async () => {
    await load();
    open.mockReturnValueOnce(closed(pipeline({ agentLabels: ['docker'] })));

    buttonOf(page(), 'Edit').click();
    await fixture.whenStable();

    expect(opened().component).toBe(PipelineDialog);
    expect(text([...page().querySelectorAll('.pairs dd')][3])).toBe('docker');
  });

  it('shows the configuration and replaces the key once confirmed', async () => {
    await load();

    await menu('config.yaml');
    http.expectOne('/api/pipelines/100/config').flush('projects: {}');
    expect(opened()).toMatchObject({
      component: CodeDialog,
      data: { code: 'projects: {}', fileName: 'cert-gui-full.yaml' },
    });

    open.mockReturnValueOnce(closed(true));
    await menu('Replace key');
    expect(opened(1).component).toBe(ConfirmDialog);
    const replaced = pipeline();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush({
      ...replaced,
      activeKey: { ...replaced.activeKey!, value: 'b'.repeat(64), hint: 'bbbbbbbb…bbbb' },
    });
    await fixture.whenStable();

    expect(text(page().querySelector('.key-value'))).toBe('b'.repeat(64));
  });

  it('invalidates the key with the reason the dialog asks for', async () => {
    await load();
    open.mockReturnValueOnce(closed(pipeline({ activeKey: null })));

    await menu('Invalidate key');

    expect(opened().component).toBe(RevokeKeyDialog);
    expect(page().querySelector('.banner.danger')).not.toBeNull();
  });

  it('deletes the pipeline once confirmed and returns to the pipelines', async () => {
    await load();
    open.mockReturnValueOnce(closed(true));

    await menu('Delete pipeline');
    http.expectOne({ method: 'DELETE', url: '/api/pipelines/100' }).flush(null);
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/pipelines']);
  });

  it('says why the pipeline could not be read and leads back to the list', async () => {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(null, { status: 500, statusText: 'Error' });
    http
      .expectOne('/api/pipelines/100')
      .flush({ detail: 'Pipeline 100 does not exist' }, { status: 404, statusText: 'Not Found' });
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toBe('Pipeline 100 does not exist');
    expect(page().querySelector('a.mat-mdc-button-base')?.getAttribute('href')).toBe('/pipelines');
    expect(text(page().querySelector('.breadcrumb'))).toBe('DevSecOps Pipelines/Pipeline');
  });
});
