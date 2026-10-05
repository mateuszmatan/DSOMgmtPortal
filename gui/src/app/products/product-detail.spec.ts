import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Product } from '../core/models';
import { pipeline, product, service, servicePipelines } from '../testing/fixtures';
import { ProductDetail } from './product-detail';

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductDetail);
    fixture.componentRef.setInput('id', '1');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;

  async function load(stored: Product = product()) {
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(stored);
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    await fixture.whenStable();
  }

  function withScm(scm: Partial<Product['services'][number]['scm']>): Product {
    const stored = service();
    return product({ services: [{ ...stored, scm: { ...stored.scm, ...scm } }] });
  }

  it('shows the product, its counts and its pipelines without icons', async () => {
    await load();

    expect(page().querySelector('h1')?.textContent).toBe('CertScanner');
    expect(page().querySelector('.breadcrumb')?.textContent).toContain(
      'DevSecOps Product Management',
    );
    expect([...page().querySelectorAll('.stat')].map((stat) => stat.textContent?.trim())).toEqual([
      '1Services',
      '1Pipelines',
      '1Active keys',
      '0Invalidated keys',
    ]);
    expect(page().querySelector('.pipeline-title strong')?.textContent).toBe('Full pipeline');
    expect(page().querySelector('mat-icon')).toBeNull();
  });

  it('links the Bitbucket repository of each service', async () => {
    await load();

    const link = page().querySelector<HTMLAnchorElement>('.repository a')!;
    expect(link.textContent).toBe('https://bitbucket.bbh.com/projects/CERT/repos/gui');
    expect(link.href).toBe('https://bitbucket.bbh.com/projects/CERT/repos/gui');
    expect(link.target).toBe('_blank');
  });

  it('builds the repository link from the Bitbucket fields when the service has no URL', async () => {
    await load(
      withScm({
        repositoryUrl: null,
        apiUrl: 'https://bitbucket.bbh.com',
        projectKey: 'TA',
        repoSlug: 'cert-scanner',
      }),
    );

    expect(page().querySelector('.repository a')?.textContent).toBe(
      'https://bitbucket.bbh.com/projects/TA/repos/cert-scanner',
    );
  });

  it('says when a service has no Bitbucket repository', async () => {
    await load(withScm({ repositoryUrl: null }));

    expect(page().querySelector('.repository a')).toBeNull();
    expect(page().querySelector('.repository .not-recorded')?.textContent).toBe('Not set');
  });

  it('reveals and hides the active key with a text button', async () => {
    await load();
    const toggle = () => page().querySelector<HTMLButtonElement>('.key .text-link')!;

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
    expect(toggle().textContent?.trim()).toBe('Show');
    expect(toggle().getAttribute('aria-label')).toBe('Show the key of the full pipeline');

    toggle().click();
    await fixture.whenStable();

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe(
      pipeline().activeKey!.value,
    );
    expect(toggle().textContent?.trim()).toBe('Hide');
  });

  it('shows only the hint of an active key whose value the API did not send', async () => {
    const stored = pipeline();
    fixture.detectChanges();
    http.expectOne('/api/products/1').flush(product());
    http
      .expectOne('/api/products/1/pipelines')
      .flush([
        servicePipelines({ pipelines: [{ ...stored, activeKey: { ...stored.activeKey!, value: null } }] }),
      ]);
    await fixture.whenStable();

    expect(page().querySelector('.key-value')?.textContent?.trim()).toBe('6f1c2d3e…9abc');
    expect(page().querySelector('.key .text-link')).toBeNull();
  });
});
