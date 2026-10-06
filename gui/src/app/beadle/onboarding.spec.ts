import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fieldOf, text } from '../testing/dom';
import { department, globalSettings, product, productSummary, service } from '../testing/fixtures';
import { Onboarding } from './onboarding';

describe('Onboarding', () => {
  let fixture: ComponentFixture<Onboarding>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Onboarding],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Onboarding);
    fixture.detectChanges();
    http
      .expectOne('/api/products')
      .flush([
        productSummary(),
        productSummary({ id: 2, name: 'Payments Hub', departmentId: null, departmentName: null }),
      ]);
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();
    wizard()['pipeline'].set('SAST');
    await next();
  });

  afterEach(() => http.verify());

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const review = () => [...page().querySelectorAll('dl.rows dt')].map(text);

  async function next() {
    wizard()['next']();
    await fixture.whenStable();
  }

  it('asks the department of a new product first and shows it in the review', async () => {
    expect(text(page().querySelector('.fields mat-label'))).toBe('Department');

    await next();

    expect(wizard()['step']()).toBe(1);
    expect(text(fieldOf(page(), 'Department')?.querySelector('mat-error'))).toBe('Required');

    wizard()['productForm'].patchValue({
      departmentId: 5,
      name: 'Trade Archive',
      appScanKeyId: 'bbh_key',
    });
    await new Promise((resolve) => setTimeout(resolve, 350));
    http.expectOne('/api/products/code-suggestion?name=Trade%20Archive').flush({ code: 'TA' });
    await next();
    wizard()['services'].set([
      {
        id: null,
        name: 'archive-api',
        description: '',
        appScanId: service().appScan.applicationId,
        tool: 'GRADLE',
        target: 'VM',
        openShiftProject: '',
      },
    ]);
    await next();

    expect(review()).toContain('Department');
    expect(wizard()['departmentName']()).toBe('Fund Services');
  });

  it('groups the products in the portal by department and asks for one a product lacks', async () => {
    wizard()['chooseMode']('existing');
    await fixture.whenStable();

    expect(
      wizard()
        ['productGroups']()
        .map((group) => [group.name, group.products.map((p) => p.id)]),
    ).toEqual([
      ['Corporate Technology', [1]],
      ['Not in a department', [2]],
    ]);

    wizard()['productId'].setValue(2);
    http.expectOne('/api/products/2').flush(product({ id: 2, departmentId: null }));
    await fixture.whenStable();
    await next();

    expect(wizard()['step']()).toBe(1);
    expect(text(fieldOf(page(), 'Department')?.querySelector('mat-error'))).toBe('Required');

    wizard()['productForm'].controls.departmentId.setValue(5);
    await next();
    await next();

    expect(wizard()['step']()).toBe(3);
    expect(wizard()['departmentName']()).toBe('Fund Services');

    await next();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/2?pipelineType=SAST' });
    expect(request.request.body.departmentId).toBe(5);
    request.flush(product({ id: 2, departmentId: 5 }));
    http.expectOne('/api/products/2/pipelines').flush([]);
    await fixture.whenStable();

    expect(wizard()['step']()).toBe(4);
  });

  it('keeps the department of a product in the portal', async () => {
    wizard()['chooseMode']('existing');
    wizard()['productId'].setValue(1);
    http.expectOne('/api/products/1').flush(product());
    await fixture.whenStable();

    expect(fieldOf(page(), 'Department')).toBeNull();
    expect(review()).toEqual(['Department', 'Owner team', 'Services']);
    expect(text(page().querySelector('dl.rows dd'))).toBe('Corporate Technology');
  });
});

describe('Onboarding without departments', () => {
  it('says why no department can be chosen', async () => {
    TestBed.configureTestingModule({
      imports: [Onboarding],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Onboarding);
    fixture.detectChanges();
    http.expectOne('/api/products').flush([productSummary()]);
    http
      .expectOne('/api/departments')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();
    fixture.componentInstance['pipeline'].set('SAST');
    fixture.componentInstance['next']();
    await fixture.whenStable();

    const error = (fixture.nativeElement as HTMLElement).querySelector('.choice-error');
    expect(text(error)).toContain('The departments could not be loaded');
    http.verify();
  });
});
