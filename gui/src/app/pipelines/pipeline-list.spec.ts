import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { MY_DEPARTMENT_KEY } from '../beadle/my-department';
import { PipelineHealth } from '../core/models';
import { PipelineDialog } from '../products/pipeline-dialog';
import { text } from '../testing/dom';
import { department, monitoringPipeline, pipelineHealth, pipelineRun } from '../testing/fixtures';
import { PipelineFilters, PipelineList, matches, sorted } from './pipeline-list';

const gui = pipelineHealth();
const api = pipelineHealth({
  pipeline: monitoringPipeline({
    id: 101,
    serviceId: 11,
    serviceName: 'api',
    type: 'SECURITY',
    jenkinsJob: 'DevSecOps/CERT/api-security',
    activeKey: null,
  }),
  status: 'DISABLED',
  lastRun: pipelineRun({ time: '2026-10-02T07:30:00Z' }),
});
const gateway = pipelineHealth({
  pipeline: monitoringPipeline({
    id: 102,
    productId: 2,
    productCode: 'PAY',
    productName: 'Payments Hub',
    serviceId: 20,
    serviceName: 'gateway',
    type: 'NEXUS_IQ',
    jenkinsJob: null,
  }),
  status: 'FAILURE',
  lastRun: null,
});

const noFilters: PipelineFilters = {
  service: '',
  product: '',
  type: 'ALL',
  job: '',
  key: 'ALL',
  status: 'ALL',
};

describe('the rows of the pipelines table', () => {
  const rows = [gui, api, gateway];
  const services = (list: PipelineHealth[]) => list.map((row) => row.pipeline.serviceName);

  it('filters by every column, the product by name or code', () => {
    const filtered = (filters: Partial<PipelineFilters>) =>
      services(rows.filter((row) => matches(row, { ...noFilters, ...filters })));

    expect(filtered({})).toEqual(['gui', 'api', 'gateway']);
    expect(filtered({ service: ' GA ' })).toEqual(['gateway']);
    expect(filtered({ product: 'pay' })).toEqual(['gateway']);
    expect(filtered({ product: 'cert' })).toEqual(['gui', 'api']);
    expect(filtered({ type: 'SECURITY' })).toEqual(['api']);
    expect(filtered({ job: 'security' })).toEqual(['api']);
    expect(filtered({ job: 'x' })).toEqual([]);
    expect(filtered({ key: 'ACTIVE' })).toEqual(['gui', 'gateway']);
    expect(filtered({ key: 'INVALIDATED' })).toEqual(['api']);
    expect(filtered({ status: 'FAILURE' })).toEqual(['gateway']);
  });

  it('sorts the type by its order, the key active first and the runs by time', () => {
    expect(services(sorted(rows, { active: 'type', direction: 'desc' }))).toEqual([
      'gateway',
      'api',
      'gui',
    ]);
    expect(services(sorted(rows, { active: 'key', direction: 'asc' }))).toEqual([
      'gui',
      'gateway',
      'api',
    ]);
    expect(services(sorted(rows, { active: 'lastRun', direction: 'desc' }))).toEqual([
      'gui',
      'api',
      'gateway',
    ]);
    expect(services(sorted(rows, { active: 'status', direction: 'asc' }))).toEqual([
      'gui',
      'gateway',
      'api',
    ]);
    expect(services(sorted(rows, { active: 'job', direction: 'asc' }))).toEqual([
      'gateway',
      'api',
      'gui',
    ]);
    expect(services(sorted(rows, { active: 'service', direction: '' }))).toEqual(services(rows));
  });
});

describe('PipelineList', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<PipelineList>;

  const page = () => fixture.nativeElement as HTMLElement;
  const shownServices = () => [...page().querySelectorAll('tbody tr td:first-child')].map(text);

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function open(departmentId: string | null) {
    if (departmentId === null) {
      localStorage.removeItem(MY_DEPARTMENT_KEY);
    } else {
      localStorage.setItem(MY_DEPARTMENT_KEY, departmentId);
    }
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineList);
    await settle();
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    await settle();
  }

  async function listed(rows = [gui, api, gateway], metricsError: string | null = null) {
    await open('3');
    http.expectOne('/api/pipelines?departmentId=3').flush({ pipelines: rows, metricsError });
    await settle();
  }

  async function choose(label: string, option: string) {
    const select = [...page().querySelectorAll<HTMLElement>('mat-select')].find(
      (element) =>
        element.getAttribute('aria-label') === label ||
        text(element.closest('mat-form-field')?.querySelector('mat-label')) === label,
    )!;
    select.click();
    await settle();
    [...document.querySelectorAll<HTMLElement>('mat-option')]
      .filter((element) => text(element) === option)
      .at(-1)!
      .click();
    await settle();
  }

  async function filter(label: string, value: string) {
    const input = page().querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  afterEach(() => {
    http.verify();
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });

  it('asks for the department first and remembers the one chosen', async () => {
    await open(null);

    expect(text(page().querySelector('h1'))).toBe('DevSecOps Pipelines');
    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'Choose your department to see its pipelines.',
    );
    expect(page().querySelector('table')).toBeNull();

    await choose('Your department', 'Fund Services');
    expect(localStorage.getItem(MY_DEPARTMENT_KEY)).toBe('5');
    http.expectOne('/api/pipelines?departmentId=5').flush({ pipelines: [], metricsError: null });
    await settle();

    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'No DevSecOps pipeline in Fund Services yet',
    );
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe('/self-service');
  });

  it('lists the pipelines of the remembered department with their key and last run', async () => {
    await listed();

    expect([...page().querySelectorAll('thead tr:first-child th')].map(text)).toEqual([
      'Service',
      'Product',
      'Type',
      'Jenkins job',
      'Key',
      'Last run',
      'Ran',
      '',
    ]);
    const cells = [...page().querySelectorAll('tbody tr:first-child td')].map(text);
    expect(cells.slice(0, 6)).toEqual([
      'gui',
      'CertScanner CERT',
      'Full',
      'DevSecOps/CERT/gui-full',
      '6f1c2d3e…9abc',
      'Success',
    ]);
    expect(cells[7]).toBe('Edit');
    const second = [...page().querySelectorAll('tbody tr:nth-child(2) td')].map(text);
    expect(second[4]).toBe('Invalidated');
    expect(page().querySelector('tbody tr:nth-child(2)')?.classList).toContain('revoked');
    const third = [...page().querySelectorAll('tbody tr:nth-child(3) td')].map(text);
    expect(third.slice(2, 4)).toEqual(['Nexus IQ GoldenFix', 'Not set']);
    expect(text(page().querySelector('.shown'))).toBe('3 of 3 pipelines');
    expect(page().querySelector('tbody td a')?.getAttribute('href')).toBe('/pipelines/100');
    expect(page().querySelector('.page-header a')?.getAttribute('href')).toBe('/self-service');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    page().querySelector<HTMLElement>('tbody tr:nth-child(3)')!.click();
    expect(navigate).toHaveBeenCalledWith(['/pipelines', 102]);
  });

  it('filters and sorts the table in its header', async () => {
    await listed();

    await filter('Filter by product', 'pay');
    expect(shownServices()).toEqual(['gateway']);
    expect(text(page().querySelector('.shown'))).toBe('1 of 3 pipelines');
    await filter('Filter by product', '');

    await choose('Filter by key', 'Invalidated');
    expect(shownServices()).toEqual(['api']);
    await choose('Filter by key', 'All');
    await choose('Filter by type', 'Nexus IQ GoldenFix');
    expect(shownServices()).toEqual(['gateway']);
    await choose('Filter by type', 'All');
    await choose('Filter by last run', 'Failed');
    expect(shownServices()).toEqual(['gateway']);
    await filter('Filter by service', 'gui');
    expect(text(page().querySelector('.no-match'))).toBe('No pipeline matches the filters.');
    await filter('Filter by service', '');
    await choose('Filter by last run', 'All');
    await filter('Filter by Jenkins job', 'api-');
    expect(shownServices()).toEqual(['api']);
    await filter('Filter by Jenkins job', '');

    const header = (label: string) =>
      [...page().querySelectorAll<HTMLElement>('thead tr:first-child th')].find(
        (cell) => text(cell) === label,
      )!;
    header('Service').click();
    await settle();
    expect(shownServices()).toEqual(['api', 'gateway', 'gui']);
    header('Service').click();
    await settle();
    expect(shownServices()).toEqual(['gui', 'gateway', 'api']);
  });

  it('saves the settings of a pipeline from its row and keeps its masked key', async () => {
    await listed();
    const saved = { ...gui.pipeline, agentLabels: ['docker'], activeKey: null };
    const opened = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(saved),
    } as unknown as MatDialogRef<unknown>);

    page()
      .querySelector<HTMLButtonElement>('button[aria-label="Edit the Full pipeline of gui"]')!
      .click();
    await settle();

    expect(opened.mock.calls[0][0]).toBe(PipelineDialog);
    expect(opened.mock.calls[0][1]?.data).toMatchObject({
      service: { serviceId: 10, serviceName: 'gui' },
      pipeline: gui.pipeline,
    });
    const cells = [...page().querySelectorAll('tbody tr:first-child td')].map(text);
    expect(cells[4]).toBe('6f1c2d3e…9abc');
  });

  it('says when the metrics or the pipelines could not be read', async () => {
    await listed([gui], 'InfluxDB timed out');

    expect(text(page().querySelector('dso-metrics-banner'))).toContain('InfluxDB timed out');

    await choose('Your department', 'Fund Services');
    http
      .expectOne('/api/pipelines?departmentId=5')
      .flush({ detail: 'Department 5 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text(page().querySelector('.banner'))).toBe('Department 5 does not exist');
  });
});
