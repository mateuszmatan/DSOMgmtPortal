import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { provideRouter } from '@angular/router';
import { ProductSummary } from '../core/models';
import {
  pipelineEvidence,
  productEvidence,
  runEvidence,
  serviceEvidence,
} from '../testing/fixtures';
import { ChangeEvidencePage } from './change-evidence';
import { evidenceText } from './evidence-text';

function summary(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: null,
    ownerTeam: 'Technology Architecture',
    departmentId: 3,
    departmentName: 'Corporate Technology',
    serviceCount: 1,
    pipelineCount: 2,
    activePipelineCount: 2,
    updatedAt: '2026-10-04T08:00:00Z',
    ...overrides,
  };
}

describe('ChangeEvidencePage', () => {
  let fixture: ComponentFixture<ChangeEvidencePage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChangeEvidencePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangeEvidencePage);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;

  async function list(products: ProductSummary[]) {
    fixture.detectChanges();
    http.expectOne('/api/products').flush(products);
    await fixture.whenStable();
  }

  async function expand(index: number) {
    page().querySelectorAll<HTMLElement>('dso-panel .accordion-button')[index].click();
    await fixture.whenStable();
  }

  it('lists the products without loading their evidence', async () => {
    await list([summary(), summary({ id: 2, code: 'PAY', name: 'PayHub', pipelineCount: 1 })]);

    expect(page().querySelector('h1')?.textContent).toBe('DevSecOps Change Evidence');
    expect(page().querySelector('.page-description')?.textContent).toBe(
      'Builds, tests and scans for ServiceNow changes',
    );
    expect(
      [...page().querySelectorAll('.panel-toggle')].map((toggle) => toggle.textContent?.trim()),
    ).toEqual(['Show', 'Show']);
    expect(
      [...page().querySelectorAll('.panel-title .name')].map((name) => name.textContent),
    ).toEqual(['CertScanner', 'PayHub']);
    expect(page().querySelectorAll('.panel-description .counts')[1].textContent).toContain(
      '1 pipeline',
    );
    http.expectNone((request) => request.url.startsWith('/api/evidence'));
  });

  it('loads the evidence of a product once, when it is opened', async () => {
    await list([summary()]);
    await expand(0);

    http.expectOne('/api/evidence/products/1').flush(productEvidence());
    await fixture.whenStable();

    expect(page().querySelector('.service-head h3')?.textContent).toBe('gui');
    expect(page().querySelector('.panel-toggle')?.textContent?.trim()).toBe('Hide');
    expect(page().querySelectorAll('dso-pipeline-evidence-card').length).toBe(1);
    expect(page().querySelector('.library-note')?.textContent).toContain(
      'older than the portal integration record no unit test counts',
    );

    await expand(0);
    await expand(0);
    http.expectNone('/api/evidence/products/1');
  });

  it('shows a failure and loads the evidence again on request', async () => {
    await list([summary()]);
    await expand(0);
    http
      .expectOne('/api/evidence/products/1')
      .flush({ detail: 'Product 1 not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toContain('Product 1 not found');

    page().querySelector<HTMLButtonElement>('.banner button')!.click();
    await fixture.whenStable();
    http.expectOne('/api/evidence/products/1').flush(productEvidence());
    await fixture.whenStable();

    expect(page().querySelector('.banner')).toBeNull();
  });

  it('shows the build, its links, the scans against their limits and the stages', async () => {
    await list([summary()]);
    await expand(0);
    http.expectOne('/api/evidence/products/1').flush(productEvidence());
    await fixture.whenStable();
    const card = page().querySelector('dso-pipeline-evidence-card')!;

    expect(card.querySelector('h4')?.textContent).toBe('Full pipeline');
    expect(card.querySelector<HTMLAnchorElement>('.build-link')?.href).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/42/',
    );
    expect([...card.querySelectorAll('.links a')].map((link) => link.textContent?.trim())).toEqual([
      'Build',
      'Pipeline report',
      'Unit test report',
      'Artifacts',
    ]);
    expect(card.querySelector('svg-icon')).toBeNull();
    expect(card.querySelector('.coverage .value')?.textContent).toBe('84.25%');
    expect(
      [...card.querySelectorAll('.stage')].map((stage) =>
        ['pass', 'fail', 'blocked'].find((status) => stage.classList.contains(status)),
      ),
    ).toEqual(['pass', 'pass', 'fail', 'blocked']);
    const nexusIq = [...card.querySelectorAll('tr')].find((row) =>
      row.textContent?.includes('Nexus IQ'),
    )!;
    expect(nexusIq.querySelector('td.bad')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('1 / 0');
    expect(card.querySelector('.gate')?.textContent).toContain('Release blocked');
  });

  it('copies the evidence of a pipeline for ServiceNow', async () => {
    const copy = vi.spyOn(TestBed.inject(Clipboard), 'copy').mockReturnValue(true);
    const evidence = productEvidence({
      services: [serviceEvidence({ pipelines: [pipelineEvidence({ run: runEvidence() })] })],
    });
    await list([summary()]);
    await expand(0);
    http.expectOne('/api/evidence/products/1').flush(evidence);
    await fixture.whenStable();

    const button = [
      ...page().querySelectorAll<HTMLButtonElement>('dso-pipeline-evidence-card button'),
    ].find((b) => b.textContent?.includes('Copy for ServiceNow'))!;
    button.click();
    await fixture.whenStable();

    const service = evidence.services[0];
    expect(copy).toHaveBeenCalledWith(evidenceText(evidence, service, service.pipelines[0]));
  });

  it('says why the products could not be listed', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/products')
      .flush({ detail: 'The database is not available' }, { status: 503, statusText: '' });
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe('The database is not available');
    expect(page().querySelector('.accordion')).toBeNull();
  });

  it('leads to the Admin products while there are no products', async () => {
    await list([]);

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No products yet');
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe('/admin/products');
  });

  it('finds products by the trimmed search term and says when none matches', async () => {
    await list([summary()]);
    const input = page().querySelector<HTMLInputElement>('input[aria-label="Find a product"]')!;

    input.value = '   ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    http.expectNone((request) => request.url === '/api/products');
    input.value = ' pay ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products?search=pay').flush([]);
    await fixture.whenStable();

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No matching products');
  });

  it('warns when the run metrics cannot be read and shows the contact', async () => {
    await list([summary()]);
    await expand(0);
    http.expectOne('/api/evidence/products/1').flush(
      productEvidence({
        metricsError: 'InfluxDB is not reachable',
        services: [serviceEvidence({ repositoryUrl: null, description: null })],
      }),
    );
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toContain(
      'The run metrics cannot be read, so the runs show as not recorded: InfluxDB is not reachable',
    );
    expect(page().querySelector<HTMLAnchorElement>('.product-facts a')?.href).toBe(
      'mailto:arch@bbh.com',
    );
    expect(page().querySelector('.identifiers dd')?.textContent?.trim()).toBe('Not recorded');
    expect(page().querySelector('.service-head .muted')).toBeNull();
  });
});
