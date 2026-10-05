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
    page().querySelectorAll<HTMLElement>('mat-expansion-panel-header')[index].click();
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
      [...page().querySelectorAll('mat-panel-title .name')].map((name) => name.textContent),
    ).toEqual(['CertScanner', 'PayHub']);
    expect(page().querySelectorAll('mat-panel-description .counts')[1].textContent).toContain(
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
      'does not record unit test counts',
    );

    await expand(0);
    await expand(0);
    http.expectNone('/api/evidence/products/1');
  });

  it('shows the values the evidence does not hold as not recorded', async () => {
    await list([summary()]);
    await expand(0);
    http
      .expectOne('/api/evidence/products/1')
      .flush(productEvidence({ ownerTeam: null, services: [serviceEvidence()] }));
    await fixture.whenStable();

    const identifiers = [...page().querySelectorAll('.identifiers > div')].map((item) => [
      item.querySelector('dt')?.textContent?.trim(),
      item.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(identifiers).toContainEqual(['Nexus IQ application', 'Not recorded']);
    expect(identifiers).toContainEqual(['SonarQube project key', 'cert-gui']);
    expect(page().querySelector('.product-facts')?.textContent).toContain('Not recorded');
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
    expect(card.querySelector('mat-icon')).toBeNull();
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

  it('says when a pipeline has no recorded run', async () => {
    await list([summary()]);
    await expand(0);
    http.expectOne('/api/evidence/products/1').flush(
      productEvidence({
        services: [
          serviceEvidence({ pipelines: [pipelineEvidence({ run: null, status: 'NO_DATA' })] }),
        ],
      }),
    );
    await fixture.whenStable();

    expect(page().querySelector('.no-run')?.textContent).toContain(
      'No run of this pipeline was recorded yet.',
    );
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
});
