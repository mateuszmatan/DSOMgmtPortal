import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  Department,
  MonitoringOverview as Overview,
  MonitoringStatus,
  PortfolioActivity,
} from '../core/models';
import { text } from '../testing/dom';
import {
  department,
  doraSummary,
  monitoringOverview,
  monitoringStatus,
  portfolioActivity,
  productHealth,
} from '../testing/fixtures';
import { MonitoringOverview } from './monitoring-overview';

describe('MonitoringOverview', () => {
  let fixture: ComponentFixture<MonitoringOverview>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MonitoringOverview],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MonitoringOverview);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLAnchorElement>('a.product')];
  const tiles = () => [...page().querySelectorAll('.stat')].map((tile) => tile.textContent?.trim());
  const headings = () => [...page().querySelectorAll('.department-title h2')].map(text);
  const fundServices = department({ id: 5, name: 'Fund Services', productCount: 1 });

  async function load(
    overview: Overview = monitoringOverview(),
    status: MonitoringStatus = monitoringStatus(),
    activity: PortfolioActivity = portfolioActivity(),
    departments: Department[] = [department(), fundServices],
  ) {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(status);
    http.expectOne('/api/monitoring/products').flush(overview);
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/monitoring/activity?range=30d').flush(activity);
    await fixture.whenStable();
  }

  async function filter(text: string) {
    const input = page().querySelector<HTMLInputElement>('input[aria-label="Filter products"]')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('totals the pipelines of every product and links each product', async () => {
    await load(
      monitoringOverview({
        products: [
          productHealth(),
          productHealth({
            productId: 2,
            code: 'PAY',
            name: 'PayHub',
            ownerTeam: null,
            departmentId: 5,
            serviceCount: 1,
            pipelineCount: 1,
            overall: 'DISABLED',
            statusCounts: { DISABLED: 1, UNSTABLE: 1 },
            lastRunAt: null,
          }),
        ],
      }),
    );

    expect(page().querySelector('h1')?.textContent).toBe('DevSecOps Pipeline Monitoring');
    expect(tiles()).toEqual([
      '5Pipelines',
      '2Succeeded',
      '2Failing or unstable',
      '1Keys invalidated',
    ]);
    expect(cards().map((card) => card.getAttribute('href'))).toEqual([
      '/monitoring/products/1',
      '/monitoring/products/2',
    ]);
    expect(cards()[0].textContent).toContain('3 pipelines · 2 services');
    expect(cards()[1].textContent).toContain('1 pipeline · 1 service');
    expect(cards()[1].textContent).toContain('No owner team');
    expect(cards()[1].textContent).toContain('No runs yet');
    expect(cards()[1].classList).toContain('overall-disabled');
    expect(headings()).toEqual(['Corporate Technology', 'Fund Services']);
    expect(
      [...page().querySelectorAll('.by-department .track')].map((track) =>
        track.getAttribute('aria-label'),
      ),
    ).toEqual([
      'Corporate Technology: 1 failed, 2 success',
      'Fund Services: 1 unstable, 1 key invalidated',
    ]);
  });

  it('charts the DORA metrics and daily runs of all pipelines over 30 days', async () => {
    await load();

    expect([...page().querySelectorAll('dso-dora-tiles .tile-title')].map(text)).toEqual([
      'Deployment frequency',
      'Lead time for changes',
      'Change failure rate',
      'Time to restore',
    ]);
    expect(text(page().querySelector('.portfolio h2'))).toBe('Activity of all pipelines');
    expect(text(page().querySelector('.portfolio .card-header .muted'))).toBe(
      '40 runs in the last 30 days',
    );
    expect(page().querySelector('.portfolio dso-activity-chart svg')).not.toBeNull();
  });

  it('leaves the portfolio charts out until a pipeline has run', async () => {
    await load(
      monitoringOverview({ products: [productHealth({ departmentId: null })] }),
      monitoringStatus({ influxConfigured: false }),
      portfolioActivity({ dora: doraSummary({ runs: 0 }) }),
      [],
    );

    expect(page().querySelector('dso-dora-tiles')).toBeNull();
    expect(page().querySelector('.portfolio')).toBeNull();
    expect(headings()).toEqual(['Not in a department']);
    expect(cards().length).toBe(1);
  });

  it('filters the products by name, code or owner team', async () => {
    await load();

    await filter(' technology ');
    expect(cards().length).toBe(1);

    await filter('payhub');
    expect(cards().length).toBe(0);
    expect(page().querySelector('.empty-state h3')?.textContent).toBe(
      'No product matches "payhub"',
    );
  });

  it('invites adding a product when there is none and reloads on refresh', async () => {
    await load(monitoringOverview({ products: [] }));

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No products yet');
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe('/products/new');

    page().querySelector<HTMLButtonElement>('.actions button')!.click();
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http.expectOne('/api/monitoring/products').flush(monitoringOverview());
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(cards().length).toBe(1);
  });

  it('explains missing or unreachable metrics', async () => {
    await load(
      monitoringOverview({ metricsError: 'InfluxDB timed out' }),
      monitoringStatus({ influxReachable: false, influxError: 'connection refused' }),
    );

    const banners = [...page().querySelectorAll('.banner')].map((banner) => banner.textContent);
    expect(banners[0]).toContain('InfluxDB cannot be reached: connection refused');
    expect(banners[1]).toContain('InfluxDB timed out');
  });

  it('shows why the overview could not be read', async () => {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(monitoringStatus());
    http
      .expectOne('/api/monitoring/products')
      .flush({ detail: 'Database unavailable' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/monitoring/activity?range=30d').flush(portfolioActivity());
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Database unavailable');
  });
});
