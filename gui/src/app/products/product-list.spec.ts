import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
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
    expect(row.querySelector('.revoked')?.textContent?.trim()).toBe('· 1 invalidated');
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

  it('searches as the user types and says when nothing matches', async () => {
    const fixture = TestBed.createComponent(ProductList);
    const http = TestBed.inject(HttpTestingController);
    const page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/products').flush([summary]);
    await fixture.whenStable();
    expect(page.querySelector('.count')?.textContent?.trim()).toBe('1 product');

    const input = page.querySelector<HTMLInputElement>('input[aria-label="Search products"]')!;
    input.value = '  payments ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products?search=payments').flush([]);
    await fixture.whenStable();

    expect(page.querySelector('.empty-state h3')?.textContent).toBe(
      'No product matches "payments"',
    );
    expect(page.querySelector('.empty-state a')).toBeNull();
    expect(page.querySelector('.count')?.textContent?.trim()).toBe('0 products');
  });

  it('shows a product without team or pipelines and opens it from its row', async () => {
    const fixture = TestBed.createComponent(ProductList);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/products')
      .flush([
        {
          ...summary,
          id: 4,
          ownerTeam: null,
          description: null,
          pipelineCount: 0,
          activePipelineCount: 0,
        },
        { ...summary, id: 5, activePipelineCount: 3 },
      ]);
    await fixture.whenStable();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
      'tr.mat-mdc-row',
    );

    expect(rows[0].querySelector('.mat-column-ownerTeam')?.textContent?.trim()).toBe('–');
    expect(rows[0].querySelector('.description')).toBeNull();
    expect(rows[0].querySelector('.mat-column-pipelines')?.textContent?.trim()).toBe('None yet');
    expect(rows[1].querySelector('.revoked')).toBeNull();
    expect(rows[1].querySelector('.pipelines')?.textContent).toContain('3 active');

    rows[0].click();
    expect(navigate).toHaveBeenCalledWith(['/products', 4]);
  });
});
