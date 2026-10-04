import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProductSummary } from '../core/models';
import { ProductList } from './product-list';

describe('ProductList', () => {
  const summary: ProductSummary = {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    serviceCount: 2,
    pipelineCount: 3,
    activePipelineCount: 2,
    updatedAt: new Date().toISOString(),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('lists the products with their services and pipelines', async () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/products').flush([summary]);
    await fixture.whenStable();
    const row = (fixture.nativeElement as HTMLElement).querySelector('tr.mat-mdc-row')!;

    expect(row.querySelector('.name')?.textContent).toBe('CertScanner');
    expect(row.querySelector('.code')?.textContent).toBe('CERT');
    expect(row.querySelector('.mat-column-services')?.textContent?.trim()).toBe('2');
    expect(row.querySelector('.revoked')?.textContent?.trim()).toBe('key_off1');
    expect(row.querySelector('.mat-column-updatedAt')?.textContent?.trim()).toBe('just now');
  });

  it('invites to add the first product', async () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/products').flush([]);
    await fixture.whenStable();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.empty-state h3')?.textContent,
    ).toBe('No products yet');
  });

  it('shows why the products could not be loaded', async () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/products')
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.banner')?.textContent).toContain(
      'cannot be reached',
    );
  });
});
