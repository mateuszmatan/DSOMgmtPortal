import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MonitoringOverview as Overview, MonitoringStatus } from '../core/models';
import { monitoringOverview, monitoringStatus, productHealth } from '../testing/fixtures';
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
  const tiles = () => [...page().querySelectorAll('.tile')].map((tile) => tile.textContent?.trim());

  async function load(
    overview: Overview = monitoringOverview(),
    status: MonitoringStatus = monitoringStatus(),
  ) {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/status').flush(status);
    http.expectOne('/api/monitoring/products').flush(overview);
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
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Database unavailable');
  });
});
