import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { buttonOf, fieldOf, text } from '../testing/dom';
import {
  anotherService,
  department,
  globalSettings,
  pipeline,
  product,
  productSummary,
  service,
  servicePipelines,
} from '../testing/fixtures';
import { SelfService } from './self-service';
import { WizardService } from './self-service-model';

const added: WizardService = {
  id: null,
  name: 'archive-api',
  description: '',
  appScanId: service().appScan.applicationId,
  tool: 'GRADLE',
  target: 'VM',
  openShiftProject: '',
  nexusIqApplication: '',
  repositoryUrl: '',
};

describe('SelfService', () => {
  let fixture: ComponentFixture<SelfService>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [SelfService],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SelfService);
    fixture.detectChanges();
    http
      .expectOne('/api/products')
      .flush([
        productSummary(),
        productSummary({ id: 2, name: 'Payments Hub', departmentId: null, departmentName: null }),
        productSummary({ id: 3, name: 'Ledger', departmentId: 5, departmentName: 'Fund Services' }),
      ]);
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const all = (selector: string) => [...page().querySelectorAll(selector)].map(text);
  const review = () => all('dl.rows dt');
  const groups = () =>
    wizard()
      ['productGroups']()
      .map((group) => [group.name, group.products.map((p) => p.id)]);

  async function next() {
    wizard()['next']();
    await fixture.whenStable();
  }

  async function click(scope: ParentNode, label: string) {
    buttonOf(scope, label).click();
    await fixture.whenStable();
  }

  async function chooseProduct(
    departmentId: number,
    stored = product(),
    pipelines = [servicePipelines()],
  ) {
    wizard()['productForm'].controls.departmentId.setValue(departmentId);
    wizard()['chooseMode']('existing');
    wizard()['productId'].setValue(stored.id);
    http.expectOne(`/api/products/${stored.id}`).flush(stored);
    TestBed.tick();
    http.expectOne(`/api/products/${stored.id}/pipelines`).flush(pipelines);
    await fixture.whenStable();
  }

  it('asks the department of a new product first and shows it in the review', async () => {
    expect(all('.step-bar .step-label')).toEqual([
      'Product',
      'Pipeline',
      'Services',
      'Review',
      'Next steps',
    ]);
    expect(text(page().querySelector('.fields mat-label'))).toBe('Department');

    await next();

    expect(wizard()['step']()).toBe(0);
    expect(text(fieldOf(page(), 'Department')?.querySelector('mat-error'))).toBe('Required');

    wizard()['productForm'].patchValue({
      departmentId: 5,
      name: 'Trade Archive',
      appScanKeyId: 'bbh_key',
    });
    await new Promise((resolve) => setTimeout(resolve, 350));
    http.expectOne('/api/products/code-suggestion?name=Trade%20Archive').flush({ code: 'TA' });
    await next();

    expect(text(page().querySelector('h2'))).toBe('Which pipeline does Trade Archive need?');
    expect(page().querySelector('.today')).toBeNull();
    expect(page().querySelector('.tile-note')).toBeNull();

    wizard()['pipeline'].set('SAST');
    await next();
    wizard()['services'].set([added]);
    await next();

    expect(review()).toEqual([
      'Product',
      'Department',
      'Owner team',
      'Contact e-mail',
      'Pipeline',
      'Added',
    ]);
    expect(wizard()['departmentName']()).toBe('Fund Services');
    expect(text(buttonOf(page(), 'Create the pipelines'))).toBe('Create the pipelines');
  });

  it('lists the products of the chosen department and those not in one, and moves one into it', async () => {
    wizard()['chooseMode']('existing');
    wizard()['productForm'].controls.departmentId.setValue(3);
    await fixture.whenStable();

    expect(groups()).toEqual([
      ['Corporate Technology', [1]],
      ['Not in a department', [2]],
    ]);

    wizard()['productForm'].controls.departmentId.setValue(5);
    await fixture.whenStable();

    expect(groups()).toEqual([
      ['Fund Services', [3]],
      ['Not in a department', [2]],
    ]);
    expect(text(fieldOf(page(), 'Product')?.querySelector('mat-hint'))).toBe(
      '2 products to choose from',
    );

    wizard()['productId'].setValue(2);
    http.expectOne('/api/products/2').flush(product({ id: 2, departmentId: null }));
    TestBed.tick();
    http.expectOne('/api/products/2/pipelines').flush([servicePipelines()]);
    await fixture.whenStable();

    expect(text(fieldOf(page(), 'Department')?.querySelector('mat-hint'))).toBe(
      'Saving moves the product into this department',
    );
    expect(review()).toEqual(['Owner team', 'Services']);

    await next();
    wizard()['pipeline'].set('SAST');
    await next();
    await next();

    expect(wizard()['step']()).toBe(3);
    expect(wizard()['departmentName']()).toBe('Fund Services');
    expect(text(buttonOf(page(), 'Save the changes'))).toBe('Save the changes');

    await next();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/2?pipelineType=SAST' });
    expect(request.request.body.departmentId).toBe(5);
    request.flush(product({ id: 2, departmentId: 5 }));
    http.expectOne('/api/products/2/pipelines').flush([]);
    await fixture.whenStable();

    expect(wizard()['step']()).toBe(4);
    expect(wizard().hasUnsavedChanges()).toBe(false);
  });

  it('forgets the product once another department is chosen', async () => {
    await chooseProduct(3);

    wizard()['productForm'].controls.departmentId.setValue(5);
    await fixture.whenStable();

    expect(wizard()['productId'].value).toBeNull();
    expect(wizard()['existing']()).toBeNull();
    expect(wizard()['services']()).toEqual([]);
  });

  it('drops a product still loading once a new product is chosen', async () => {
    wizard()['chooseMode']('existing');
    wizard()['productId'].setValue(1);
    const request = http.expectOne('/api/products/1');

    wizard()['chooseMode']('new');
    await fixture.whenStable();

    expect(request.cancelled).toBe(true);
    expect(wizard()['existing']()).toBeNull();
    expect(wizard()['productId'].value).toBeNull();
  });

  it('continues only with the product chosen last once it is loaded', async () => {
    await chooseProduct(3);
    wizard()['productId'].setValue(2);
    const second = http.expectOne('/api/products/2');

    await next();

    expect(wizard()['step']()).toBe(0);
    expect(wizard()['existing']()).toBeNull();
    expect(text(page().querySelector('.choice-error'))).toBe('Wait until the product is loaded');

    second.flush(product({ id: 2, name: 'Payments Hub', departmentId: null, services: [] }));
    TestBed.tick();
    http.expectOne('/api/products/2/pipelines').flush([]);
    await next();

    expect(wizard()['step']()).toBe(1);
    expect(wizard()['productName']()).toBe('Payments Hub');
  });

  it('says why the chosen product could not be loaded and keeps to the products in the portal', async () => {
    await chooseProduct(3);
    wizard()['productId'].setValue(2);
    http
      .expectOne('/api/products/2')
      .flush({ detail: 'Product 2 was not found' }, { status: 404, statusText: 'Not Found' });

    await next();

    expect(wizard()['step']()).toBe(0);
    expect(wizard()['mode']()).toBe('existing');
    expect(wizard()['existing']()).toBeNull();
    expect(text(page().querySelector('.choice-error'))).toBe(
      'The product could not be loaded: Product 2 was not found',
    );
    expect(fieldOf(page(), 'Product name')).toBeNull();
  });

  it('shows the pipelines every service has today and how many services have each one', async () => {
    await chooseProduct(3, product({ services: [service(), anotherService()] }), [
      servicePipelines({ pipelines: [pipeline({ type: 'SAST' }), pipeline()] }),
      servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [pipeline()] }),
      servicePipelines({ serviceId: 12, serviceName: 'batch', pipelines: [] }),
    ]);
    await next();

    expect(text(page().querySelector('.lead'))).toBe(
      'A pipeline checks your code automatically every time it runs. Choosing one adds it to every service that lacks it and keeps the other pipelines.',
    );
    expect(all('.today li')).toEqual([
      'gui · Full, Static scan',
      'api · Full',
      'batch · no pipeline yet',
    ]);
    expect(all('.tile-label')).toEqual(['Static scan', 'Nexus IQ GoldenFix', 'Security', 'Full']);
    expect(all('.tile-note')).toEqual([
      '1 of 3 services has it',
      'No service has it yet',
      'No service has it yet',
      '2 of 3 services have it',
    ]);
  });

  it('says why the pipelines of a product in the portal are missing', async () => {
    wizard()['productForm'].controls.departmentId.setValue(3);
    wizard()['chooseMode']('existing');
    wizard()['productId'].setValue(1);
    http.expectOne('/api/products/1').flush(product());
    TestBed.tick();
    http
      .expectOne('/api/products/1/pipelines')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    await next();

    expect(text(page().querySelector('.choice-error'))).toBe(
      'Its pipelines could not be loaded: Database unavailable',
    );
    expect(page().querySelector('.tile-note')).toBeNull();
  });

  it('removes a service in the portal only on save and warns about its pipelines', async () => {
    await chooseProduct(3, product({ services: [service(), anotherService()] }), [
      servicePipelines({ pipelines: [pipeline(), pipeline({ type: 'SAST' })] }),
      servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [pipeline()] }),
    ]);
    await next();
    wizard()['pipeline'].set('SECURITY');
    await next();
    const rows = () => [...page().querySelectorAll<HTMLElement>('.service-list li')];
    const names = () =>
      [...page().querySelectorAll('.service-list button')].map((button) =>
        button.getAttribute('aria-label'),
      );

    expect(names()).toEqual(['Change gui', 'Remove gui', 'Change api', 'Remove api']);

    await click(rows()[0], 'Remove');

    expect(names()).toEqual(['Undo removing gui', 'Change api', 'Remove api']);

    expect(rows()[0].classList).toContain('removed');
    expect(text(rows()[0].querySelector('.tag'))).toBe('Removed');
    expect(text(rows()[0].querySelector('.removal'))).toBe(
      'Saving deletes gui, its pipelines (Full, Static scan) and their keys. Jenkins jobs that use these keys stop working.',
    );
    expect(wizard().hasUnsavedChanges()).toBe(true);

    await click(rows()[1], 'Remove');
    await next();

    expect(wizard()['step']()).toBe(2);
    expect(text(page().querySelector('.choice-error'))).toBe('Keep at least one service');

    await click(rows()[1], 'Undo');
    await next();

    expect(review()).toEqual(['Product', 'Department', 'Pipeline', 'Removed', 'Unchanged']);
    expect(all('.review-list li')).toEqual([
      'gui · Gradle · runs on Virtual machines',
      'api · Gradle · runs on Virtual machines',
    ]);
    expect(all('.removal-warning li')).toEqual(['gui · Full, Static scan']);

    await next();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1?pipelineType=SECURITY' });
    expect(request.request.body.services.map((entry: { id: number }) => entry.id)).toEqual([11]);
    request.flush(product({ services: [anotherService()] }));
    http.expectOne('/api/products/1/pipelines').flush([]);
    await fixture.whenStable();

    wizard()['restart']();
    TestBed.tick();
    http.expectOne('/api/products').flush([productSummary()]);
    await fixture.whenStable();

    expect(wizard()['step']()).toBe(0);
    expect(wizard()['removed']()).toEqual([]);
    expect(wizard()['services']()).toEqual([]);
  });

  it('removes a service not saved yet at once', async () => {
    await chooseProduct(3);
    await next();
    wizard()['pipeline'].set('SAST');
    await next();
    wizard()['services'].update((services) => [...services, added]);
    await fixture.whenStable();

    await click(page().querySelectorAll('.service-list li')[1], 'Remove');

    expect(
      wizard()
        ['services']()
        .map((entry) => entry.name),
    ).toEqual(['gui']);
    expect(wizard()['removed']()).toEqual([]);
  });

  it('says how a service in the portal changes and sends its new build tool with default build settings', async () => {
    await chooseProduct(3);
    await next();
    wizard()['pipeline'].set('FULL');
    await next();
    wizard()['services'].update(([gui]) => [{ ...gui, name: 'web', tool: 'MAVEN' }]);
    await fixture.whenStable();

    expect(text(page().querySelector('.service-list .tag'))).toBe('Changed');

    await next();

    expect(review()).toContain('Changed');
    expect(text(page().querySelector('.review-list li'))).toBe(
      'web · renamed from gui; built with Maven instead of Gradle, with the default build settings',
    );
    expect(page().querySelector('.removal-warning')).toBeNull();

    await next();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1?pipelineType=FULL' });
    const [sent] = request.request.body.services;
    expect(sent.id).toBe(10);
    expect(sent.name).toBe('web');
    expect(sent.build).toEqual(
      expect.objectContaining({ tool: 'MAVEN', autoSetup: true, buildPath: 'target/*.jar' }),
    );
    expect(sent.deployment).toEqual(service().deployment);
    expect(sent.testJobs).toEqual(service().testJobs);
    request.flush(product());
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    await fixture.whenStable();

    expect(text(page().querySelector('a[href="/admin/products/1"]'))).toBe(
      'Open in DevSecOps Admin',
    );
    expect(buttonOf(page(), 'Copy the Jenkinsfile of gui')).toBeDefined();
  });

  it('asks where the services added for a static scan run once the pipeline deploys them', async () => {
    const unplaced: WizardService = { ...added, target: null };
    wizard()['pipeline'].set('SECURITY');
    wizard()['services'].set([unplaced]);
    wizard()['step'].set(2);
    await next();

    expect(wizard()['step']()).toBe(2);
    expect(text(page().querySelector('.choice-error'))).toBe(
      'Choose where these services run: archive-api',
    );

    wizard()['services'].set([{ ...unplaced, target: 'OPENSHIFT' }]);
    await next();
    expect(wizard()['step']()).toBe(2);

    wizard()['services'].set([
      { ...unplaced, target: 'OPENSHIFT', openShiftProject: 'ta-archive' },
    ]);
    await next();
    expect(wizard()['step']()).toBe(3);
    expect(text(page().querySelector('.review-list li'))).toBe(
      'archive-api · Gradle · runs on OpenShift · project ta-archive',
    );

    wizard()['pipeline'].set('SAST');
    await fixture.whenStable();
    expect(text(page().querySelector('.review-list li'))).toBe('archive-api · Gradle');
  });

  it('asks for the Nexus IQ application and repository of every service and points to the golden pull requests', async () => {
    await chooseProduct(
      3,
      product({ services: [service(), anotherService({ nexusIqApplications: [] })] }),
      [servicePipelines(), servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [] })],
    );
    await next();
    wizard()['pipeline'].set('NEXUS_IQ');
    await fixture.whenStable();

    expect(all('.prepare li').at(-1)).toBe(
      'The Nexus IQ application and the Bitbucket repository of each service',
    );

    await next();
    await next();

    expect(wizard()['step']()).toBe(2);
    expect(text(page().querySelector('.choice-error'))).toBe(
      'Add the Nexus IQ application and Bitbucket repository of these services: api',
    );

    wizard()['services'].update(([gui, api]) => [gui, { ...api, nexusIqApplication: 'cert-api' }]);
    await next();

    expect(wizard()['step']()).toBe(3);
    expect(all('.review-list li')).toEqual([
      'api · new Nexus IQ application',
      'gui · Gradle · runs on Virtual machines · Nexus IQ cert-gui',
    ]);

    await next();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1?pipelineType=NEXUS_IQ' });
    expect(request.request.body.services[1].nexusIqApplications).toEqual([
      {
        application: 'cert-api',
        scanPatterns: ['**/build/libs/*.jar'],
        stage: 'build',
        failOnNetworkError: false,
      },
    ]);
    expect(request.request.body.services[0].nexusIqApplications).toEqual(
      service().nexusIqApplications,
    );
    request.flush(product());
    http.expectOne('/api/products/1/pipelines').flush([
      servicePipelines({
        pipelines: [
          pipeline(),
          pipeline({ id: 101, type: 'NEXUS_IQ', entryPoint: 'devSecOpsNexusIqGoldenFixPipeline' }),
        ],
      }),
    ]);
    await fixture.whenStable();

    expect(all('.next-steps > li h3')).toEqual([
      'Put the Jenkinsfile in each repository',
      'Create a Jenkins job for each service',
      'Run each job once',
      'Review the golden pull requests',
      'Follow the results',
    ]);
    expect(all('.next-steps code').at(-1)).toBe('DevSecOps/CERT/gui-nexusiq');
    expect(text(page().querySelector('.jenkinsfile .code-block'))).toContain(
      'devSecOpsNexusIqGoldenFixPipeline(',
    );
  });

  it('keeps the department of a product in the portal', async () => {
    await chooseProduct(3);

    expect(text(fieldOf(page(), 'Department')?.querySelector('mat-hint'))).toBe('');
    expect(review()).toEqual(['Owner team', 'Services']);
    expect(wizard()['departmentName']()).toBe('Corporate Technology');
  });
});

describe('SelfService without departments', () => {
  it('says why no department can be chosen and still takes a product that has one', async () => {
    TestBed.configureTestingModule({
      imports: [SelfService],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SelfService);
    const wizard = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne('/api/products').flush([productSummary()]);
    http
      .expectOne('/api/departments')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();

    const error = (fixture.nativeElement as HTMLElement).querySelector('.choice-error');
    expect(text(error)).toContain('The departments could not be loaded');

    wizard['chooseMode']('existing');
    wizard['productId'].setValue(1);
    http.expectOne('/api/products/1').flush(product());
    TestBed.tick();
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    wizard['next']();
    await fixture.whenStable();

    expect(wizard['step']()).toBe(1);
    expect(wizard['departmentName']()).toBe('Corporate Technology');
    http.verify();
  });
});

describe('SelfService without products', () => {
  it('says why no product in the portal can be chosen', async () => {
    TestBed.configureTestingModule({
      imports: [SelfService],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SelfService);
    fixture.detectChanges();
    http
      .expectOne('/api/products')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();

    expect(text((fixture.nativeElement as HTMLElement).querySelector('.choice-error'))).toBe(
      'The products could not be loaded: Database unavailable',
    );
    http.verify();
  });
});
