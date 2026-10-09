import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ProductMonitoring } from '../core/models';
import {
  monitoringPipeline,
  pipelineHealth,
  pipelineRun,
  productMonitoring,
} from '../testing/fixtures';
import { ProductMonitoringPage } from './product-monitoring';
import { buttonOf } from '../testing/dom';

describe('ProductMonitoringPage', () => {
  let fixture: ComponentFixture<ProductMonitoringPage>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductMonitoringPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ProductMonitoringPage);
    fixture.componentRef.setInput('id', '1');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => [...page().querySelectorAll<HTMLElement>('tr.mat-mdc-row')];

  async function load(data: ProductMonitoring = productMonitoring()) {
    fixture.detectChanges();
    http.expectOne('/api/monitoring/products/1').flush(data);
    await fixture.whenStable();
  }

  it('lists the pipelines of the product with the latest run linked to its build', async () => {
    await load();

    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
    expect(page().querySelector('.summary')?.textContent).toContain('1 pipeline');
    const link = rows()[0].querySelector<HTMLAnchorElement>('a.build-link')!;
    expect(link.getAttribute('href')).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    );
    expect(link.textContent?.trim()).toBe('#42');
    expect(rows()[0].querySelector('.mat-column-lastRun')?.textContent).toContain('develop');
    expect(rows()[0].querySelector('.stages')?.textContent).toContain('/ 12');
  });

  it('opens a pipeline when its row is clicked', async () => {
    await load();

    rows()[0].click();

    expect(router.navigate).toHaveBeenCalledWith(['/monitoring/pipelines', 100]);
  });

  it('invites adding pipelines to a product without any', async () => {
    await load(productMonitoring({ pipelines: [], description: null }));

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No pipelines yet');
    expect(page().querySelector('.page-header p')?.textContent).toBe(
      'Status of the latest run of each pipeline.',
    );
  });

  it('shows why the product could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/products/1')
      .flush({ detail: 'Product 1 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toBe('Product 1 was not found');
  });

  it('reads the product again on refresh and shows the progress meanwhile', async () => {
    await load();
    const refresh = buttonOf(page(), 'Refresh');

    refresh.click();
    TestBed.tick();

    expect(page().querySelector('mat-progress-bar')).not.toBeNull();
    expect(refresh.disabled).toBe(true);
    http.expectOne('/api/monitoring/products/1').flush(productMonitoring());
    await fixture.whenStable();

    expect(page().querySelector('mat-progress-bar')).toBeNull();
    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
  });
});
