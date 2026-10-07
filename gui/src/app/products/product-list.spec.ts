import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Department, ProductSummary } from '../core/models';
import { text } from '../testing/dom';
import { department, productSummary } from '../testing/fixtures';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  let http: HttpTestingController;

  const summary = productSummary();
  const fundServices = department({
    id: 5,
    name: 'Fund Services',
    productCount: 0,
    serviceCount: 0,
    pipelineCount: 0,
    activePipelineCount: 0,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductList);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLElement>('section.department')];
  const card = (name: string) =>
    cards().find((section) => text(section.querySelector('h2')) === name)!;
  async function load(
    products: ProductSummary[] = [summary],
    departments: Department[] = [department(), fundServices],
  ) {
    fixture.detectChanges();
    http.expectOne('/api/products').flush(products);
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  it('lists the products of every department with the tally of its pipelines', async () => {
    await load();
    const row = card('Corporate Technology').querySelector('tr.mat-mdc-row')!;

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
    expect(text(card('Corporate Technology').querySelector('.tally'))).toBe(
      '3 DevSecOps pipelines for 1 product · 2 active',
    );
    expect(text(card('Fund Services').querySelector('.tally'))).toBe(
      '0 DevSecOps pipelines for 0 products',
    );
    expect(text(card('Fund Services').querySelector('.no-products'))).toBe(
      'No products in Fund Services yet.',
    );
    expect(text(page().querySelector('.count'))).toBe('1 product in 2 departments');
    expect(row.querySelector('.name')?.textContent).toBe('CertScanner');
    expect(row.querySelector('.code')?.textContent).toBe('CERT');
    expect(row.querySelector('.mat-column-services')?.textContent?.trim()).toBe('2');
    expect(row.querySelector('.revoked')?.textContent?.trim()).toBe('· 1 invalidated');
    expect(row.querySelector('.mat-column-updatedAt')?.textContent?.trim()).toBe('just now');
    expect(page().querySelector('.hint')).toBeNull();
  });

  it('adds a product to a department from its card', async () => {
    await load();
    const addProduct = card('Fund Services').querySelector<HTMLAnchorElement>('a')!;

    expect(text(addProduct)).toBe('Add product');
    expect(addProduct.getAttribute('href')).toBe('/admin/products/new?department=5');
    expect(page().querySelector('section.chart')).toBeNull();
    expect(page().querySelector('h1')).toBeNull();
  });

  it('gathers the products without a department in a last card summed from their rows', async () => {
    await load([
      summary,
      productSummary({ id: 7, name: 'Ledger', departmentId: null, departmentName: null }),
      productSummary({
        id: 8,
        name: 'Archive',
        departmentId: null,
        departmentName: null,
        pipelineCount: 1,
        activePipelineCount: 1,
      }),
    ]);
    const unassigned = cards().at(-1)!;

    expect(text(unassigned.querySelector('h2'))).toBe('Not in a department');
    expect(text(unassigned.querySelector('.tally'))).toBe(
      '4 DevSecOps pipelines for 2 products · 3 active',
    );
    expect(text(unassigned.querySelector('.hint'))).toBe(
      'Edit these products to choose their department.',
    );
    expect(unassigned.querySelectorAll('tr.mat-mdc-row').length).toBe(2);
    expect(text(page().querySelector('.count'))).toBe(
      '1 product in 2 departments · 2 not in a department',
    );
  });

  it('shows no partial tally for the products without a department while searching', async () => {
    await load();
    const input = page().querySelector<HTMLInputElement>('input[aria-label="Search products"]')!;
    input.value = 'ledger';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http
      .expectOne('/api/products?search=ledger')
      .flush([productSummary({ id: 7, name: 'Ledger', departmentId: null, departmentName: null })]);
    await fixture.whenStable();

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Not in a department',
    ]);
    expect(cards()[0].querySelector('.tally')).toBeNull();
    expect(text(page().querySelector('.count'))).toBe(
      '0 products in 0 departments · 1 not in a department',
    );
  });

  it('invites to add the first product above the departments waiting for one', async () => {
    await load([]);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No products yet');
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe(
      '/admin/products/new',
    );
    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
  });

  it('invites to add the first product when there are no departments either', async () => {
    await load([], []);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No products yet');
    expect(cards().length).toBe(0);
  });

  it('shows why the products could not be loaded', async () => {
    fixture.detectChanges();
    http.expectOne('/api/products').flush(null, { status: 0, statusText: 'Unknown Error' });
    http.expectOne('/api/departments').flush([department()]);
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toContain('cannot be reached');
  });

  it('searches as the user types and shows only the departments with matches', async () => {
    await load();
    expect(text(page().querySelector('.count'))).toBe('1 product in 2 departments');

    const input = page().querySelector<HTMLInputElement>('input[aria-label="Search products"]')!;
    input.value = '  cert ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products?search=cert').flush([summary]);
    await fixture.whenStable();

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
    ]);
    expect(text(page().querySelector('.count'))).toBe('1 product in 1 department');

    input.value = 'payments';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
    http.expectOne('/api/products?search=payments').flush([]);
    await fixture.whenStable();

    expect(text(page().querySelector('.empty-state h3'))).toBe('No product matches "payments"');
    expect(page().querySelector('.empty-state a')).toBeNull();
    expect(text(page().querySelector('.count'))).toBe('0 products in 0 departments');
  });

  it('shows a product without team or pipelines and opens it from its row', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await load([
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
    const rows = page().querySelectorAll<HTMLElement>('tr.mat-mdc-row');

    expect(rows[0].querySelector('.mat-column-ownerTeam')?.textContent?.trim()).toBe('–');
    expect(rows[0].querySelector('.description')).toBeNull();
    expect(rows[0].querySelector('.mat-column-pipelines')?.textContent?.trim()).toBe('None yet');
    expect(rows[1].querySelector('.revoked')).toBeNull();
    expect(rows[1].querySelector('.pipelines')?.textContent).toContain('3 active');

    rows[0].click();
    expect(navigate).toHaveBeenCalledWith(['/admin/products', 4]);
  });
});
