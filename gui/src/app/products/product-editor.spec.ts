import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import {
  anotherService,
  globalSettings,
  product,
  service,
  servicePipelines,
} from '../testing/fixtures';
import { GeneratedKeys } from './generated-keys';
import { ProductEditor } from './product-editor';

describe('ProductEditor', () => {
  let fixture: ComponentFixture<ProductEditor>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductEditor],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ProductEditor);
  });

  afterEach(() => http.verify());

  const editor = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;

  async function start() {
    await fixture.whenStable();
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  async function edit(services = [service(), anotherService()]) {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http.expectOne('/api/products/1').flush(product({ services }));
    http
      .expectOne('/api/products/1/pipelines')
      .flush([
        servicePipelines(),
        servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [] }),
      ]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();
  }

  const names = () =>
    [...page().querySelectorAll('mat-expansion-panel .service-name')].map((name) =>
      name.textContent?.trim(),
    );
  const button = (label: string) =>
    [...page().querySelectorAll<HTMLButtonElement>('button')].find(
      (element) =>
        element.getAttribute('aria-label') === label || element.textContent?.trim() === label,
    )!;
  const confirming = (answer: boolean) =>
    vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(answer) } as unknown as MatDialogRef<unknown>);

  const imageBuild = {
    RD: {
      projectBuild: 'cert-build',
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: 'target/docker',
      dockerRepoPush: 'nexus.bbh.com:18444/cert',
      nexusAuthFile: '/etc/containers/auth.json',
    },
  };

  function fillValidProduct() {
    editor()['form'].patchValue({
      code: 'CERT',
      name: 'CertScanner',
      appScan: { keyId: 'bbh_key' },
    });
    editor()
      ['form'].controls.services.at(0)
      .patchValue({
        name: 'gui',
        build: { javaPath: '/usr/lib/jvm/java-17-openjdk', command: { tasks: 'clean package' } },
        deployment: { appName: 'gui', artifactName: 'gui.jar' },
        openShiftTargets: imageBuild,
        appScan: { applicationId: '109f44ac-cc06-4ca0-884e-d944904f7019' },
      });
  }

  it('starts a new product with one open service', async () => {
    await start();

    expect(page().querySelector('h1')?.textContent).toBe('Add product');
    expect(page().querySelectorAll('mat-expansion-panel').length).toBe(1);
    expect(editor()['expanded']()).toBe(0);
    expect(editor().hasUnsavedChanges()).toBe(false);
  });

  it('starts the new service from the service defaults of the global settings', async () => {
    await start();
    const value = editor()['form'].controls.services.at(0).getRawValue();

    expect(value.build.tool).toBe('MAVEN');
    expect(value.build.sourceDir).toBe('app');
    expect(value.deployment.target).toBe('OPENSHIFT');
  });

  it('works with the library defaults when the global settings cannot be read', async () => {
    await fixture.whenStable();
    http.expectOne('/api/settings').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    const value = editor()['form'].controls.services.at(0).getRawValue();

    expect(page().querySelectorAll('mat-expansion-panel').length).toBe(1);
    expect(value.build.tool).toBe('GRADLE');
    expect(value.deployment.target).toBe('VM');
  });

  it('sends nothing while fields are invalid and says so', async () => {
    await start();
    await submit();

    http.expectNone('/api/products');
    expect(page().querySelector('.save-error')?.textContent).toContain(
      'Some fields need your attention.',
    );
    expect(page().querySelector('mat-expansion-panel.has-errors')).not.toBeNull();
  });

  it('adds the product and opens it', async () => {
    await start();
    fillValidProduct();
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/products' });
    expect(request.request.body).toMatchObject({
      code: 'CERT',
      version: null,
      services: [{ id: null, name: 'gui' }],
    });
    request.flush(product({ id: 5 }));
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/products', 5]);
    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(TestBed.inject(GeneratedKeys).take(5)).toEqual(['gui']);
  });

  it('marks the fields the API refused and lists the problems without a field', async () => {
    await start();
    editor()['addService']();
    fillValidProduct();
    editor()
      ['form'].controls.services.at(1)
      .patchValue({
        name: 'api',
        build: { javaPath: '/usr/lib/jvm/java-21-openjdk', command: { tasks: 'clean package' } },
        deployment: { appName: 'api', artifactName: 'api.jar' },
        openShiftTargets: imageBuild,
        appScan: { applicationId: '209f44ac-cc06-4ca0-884e-d944904f7019' },
      });
    await submit();

    http.expectOne('/api/products').flush(
      {
        detail: 'The request has invalid values',
        errors: [
          { field: 'services[1].sonar.projectKey', message: 'is already used by PAYHUB/gateway' },
          { field: 'appScanAccount', message: 'is unknown' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(
      editor()['form'].controls.services.at(1).controls.sonar.controls.projectKey.errors,
    ).toEqual({
      server: 'is already used by PAYHUB/gateway',
    });
    expect(editor()['expanded']()).toBe(1);
    expect(page().querySelector('.problems')?.textContent).toContain('appScanAccount: is unknown');
    expect(page().querySelector('.mat-expanded .rail-item.active')?.textContent).toContain(
      'SonarQube',
    );
  });

  it('saves again once a name the API saw twice is fixed on the other service', async () => {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http
      .expectOne('/api/products/1')
      .flush(product({ services: [service(), anotherService({ name: 'api' })] }));
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();

    await submit();
    http.expectOne({ method: 'PUT', url: '/api/products/1' }).flush(
      {
        detail: 'The request has invalid values',
        errors: [
          {
            field: 'services[1].name',
            message: 'another service of this product already uses this name',
          },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    const services = editor()['form'].controls.services;
    expect(services.at(1).controls.name.hasError('server')).toBe(true);

    services.at(0).controls.name.setValue('web');
    await submit();

    http.expectOne({ method: 'PUT', url: '/api/products/1' }).flush(product());
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalledWith(['/products', 1]);
  });

  it('refuses two services with the same name before asking the API', async () => {
    await start();
    editor()['addService']();
    fillValidProduct();
    editor()
      ['form'].controls.services.at(1)
      .patchValue({
        name: 'gui',
        build: { javaPath: '/usr/lib/jvm/java-21-openjdk', command: { tasks: 'clean package' } },
        deployment: { appName: 'api', artifactName: 'api.jar' },
        openShiftTargets: imageBuild,
        appScan: { applicationId: '209f44ac-cc06-4ca0-884e-d944904f7019' },
      });
    await submit();

    http.expectNone('/api/products');
    expect(editor()['form'].controls.services.at(1).controls.name.errors).toEqual({
      rule: 'another service of this product already uses this name',
    });
    expect(editor()['expanded']()).toBe(1);
  });

  it('shows a conflict the API reports', async () => {
    await start();
    fillValidProduct();
    await submit();

    http
      .expectOne('/api/products')
      .flush(
        { detail: 'Product code CERT is already used by CertScanner' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(page().querySelector('.save-error')?.textContent).toContain(
      'Product code CERT is already used by CertScanner',
    );
  });

  it('loads a stored product and saves it with its version', async () => {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http
      .expectOne('/api/products/1')
      .flush(product({ services: [service(), anotherService({ name: 'api' })] }));
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();

    expect(page().querySelector('h1')?.textContent).toBe('Edit CertScanner');
    expect(page().querySelector('.tag')?.textContent?.trim()).toBe('1 pipeline');

    editor()['move'](1, -1);
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.version).toBe(3);
    expect(request.request.body.services.map((s: { name: string }) => s.name)).toEqual([
      'api',
      'gui',
    ]);
    request.flush(product());
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalledWith(['/products', 1]);
    expect(TestBed.inject(GeneratedKeys).take(1)).toEqual([]);
  });

  it('tells the product page which services were added, so it can announce their keys', async () => {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http.expectOne('/api/products/1').flush(product());
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    http.expectOne('/api/settings').flush(globalSettings());
    await fixture.whenStable();

    editor()['duplicate'](0);
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.services.map((s: { id: number | null }) => s.id)).toEqual([
      10,
      null,
    ]);
    request.flush(product({ services: [service(), service({ id: 12, name: 'gui-copy' })] }));
    await fixture.whenStable();

    expect(TestBed.inject(GeneratedKeys).take(1)).toEqual(['gui-copy']);
  });

  it('announces no keys when the save fails', async () => {
    await start();
    fillValidProduct();
    await submit();

    http
      .expectOne({ method: 'POST', url: '/api/products' })
      .flush({ detail: 'The database is not available' }, { status: 503, statusText: '' });
    await fixture.whenStable();

    expect(page().querySelector('.save-error')?.textContent).toContain(
      'The database is not available',
    );
    expect(TestBed.inject(GeneratedKeys).take(5)).toEqual([]);
  });

  it('says why a stored product could not be loaded and leads back to the list', async () => {
    fixture.componentRef.setInput('id', '9');
    await fixture.whenStable();
    const pipelines = http.expectOne('/api/products/9/pipelines');
    const settings = http.expectOne('/api/settings');
    http
      .expectOne('/api/products/9')
      .flush({ detail: 'Product 9 does not exist' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(pipelines.cancelled && settings.cancelled).toBe(true);

    expect(page().querySelector('.banner')?.textContent).toBe('Product 9 does not exist');
    expect(page().querySelector('a[href="/products"].mat-mdc-button-base')).not.toBeNull();
    expect(page().querySelector('form')).toBeNull();
    expect(page().querySelector('h1')?.textContent).toBe('Edit product');
  });

  it('shows the pipelines of each stored service and opens none of several', async () => {
    await edit();

    expect(names()).toEqual(['gui', 'api']);
    expect(page().querySelectorAll('mat-panel-description .tag')[0]?.textContent?.trim()).toBe(
      '1 pipeline',
    );
    expect(page().querySelectorAll('mat-panel-description .tag').length).toBe(1);
    expect(editor()['expanded']()).toBeNull();
  });

  it('moves services up and down and keeps the open one open', async () => {
    await edit();
    editor()['panelToggled'](0, true);
    await fixture.whenStable();

    button('Move gui down').click();
    await fixture.whenStable();
    expect(names()).toEqual(['api', 'gui']);
    expect(editor()['expanded']()).toBe(1);

    button('Move api down').click();
    await fixture.whenStable();
    expect(names()).toEqual(['gui', 'api']);
    expect(editor()['expanded']()).toBe(0);
    expect(button('Move gui up').disabled).toBe(true);
    expect(editor().hasUnsavedChanges()).toBe(true);
  });

  it('closes a service panel without opening another', async () => {
    await edit();
    editor()['panelToggled'](1, true);
    editor()['panelToggled'](0, false);
    expect(editor()['expanded']()).toBe(1);

    editor()['panelToggled'](1, false);
    expect(editor()['expanded']()).toBeNull();
  });

  it('asks before removing a stored service with pipelines and keeps it when cancelled', async () => {
    await edit();
    const open = confirming(false);

    editor()['remove'](0);
    await fixture.whenStable();

    expect(open.mock.calls[0][0]).toBe(ConfirmDialog);
    expect(open.mock.calls[0][1]?.data).toMatchObject({
      title: 'Remove gui?',
      message: expect.stringContaining("deletes the service's pipeline and keys"),
      danger: true,
    });
    expect(names()).toEqual(['gui', 'api']);
  });

  it('removes a stored service once confirmed and sends the product without it', async () => {
    await edit();
    const open = confirming(true);
    editor()['panelToggled'](1, true);

    editor()['remove'](0);
    await fixture.whenStable();

    expect((open.mock.calls[0][1]?.data as ConfirmDialogData).message).toContain(
      'Saving the product deletes',
    );
    expect(names()).toEqual(['api']);
    expect(editor()['expanded']()).toBe(0);
    await submit();
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.services.map((s: { id: number }) => s.id)).toEqual([11]);
    request.flush(product({ services: [anotherService()] }));
    await fixture.whenStable();
  });

  it('says a stored service without pipelines is only removed on save', async () => {
    await edit();
    const open = confirming(true);
    editor()['panelToggled'](1, true);

    editor()['remove'](1);
    await fixture.whenStable();

    expect((open.mock.calls[0][1]?.data as ConfirmDialogData).message).toBe(
      'The service is removed when you save the product.',
    );
    expect(names()).toEqual(['gui']);
    expect(editor()['expanded']()).toBeNull();
  });

  it('offers to add a service once the last one is removed', async () => {
    await start();
    editor()['remove'](0);
    await fixture.whenStable();

    expect(page().querySelector('.empty-state h3')?.textContent).toBe('No services yet');

    page().querySelector<HTMLButtonElement>('.empty-state button')!.click();
    await fixture.whenStable();
    expect(page().querySelectorAll('mat-expansion-panel').length).toBe(1);
    expect(editor()['expanded']()).toBe(0);
  });

  it('cancels back to the product or to the list', async () => {
    await start();
    button('Cancel').click();
    expect(router.navigate).toHaveBeenLastCalledWith(['/products']);
  });

  it('cancels back to the stored product', async () => {
    await edit();
    button('Cancel').click();
    expect(router.navigate).toHaveBeenLastCalledWith(['/products', 1]);
  });

  it('turns the product code into upper case as it is typed', async () => {
    await start();
    const input = page().querySelector<HTMLInputElement>('input[formControlName=code]')!;

    input.value = 'cert';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(input.value).toBe('CERT');
    expect(input.classList).not.toContain('uppercase');
    expect(editor()['form'].controls.code.valid).toBe(true);
  });

  it('removes a service that was never saved without asking', async () => {
    await start();
    editor()['addService']();
    editor()['remove'](1);
    await fixture.whenStable();

    expect(editor()['form'].controls.services.length).toBe(1);
    expect(editor().hasUnsavedChanges()).toBe(true);
  });

  it('duplicates a service right after it', async () => {
    await start();
    editor()['form'].controls.services.at(0).patchValue({ name: 'gui' });
    editor()['duplicate'](0);
    await fixture.whenStable();

    expect(editor()['form'].controls.services.controls.map((s) => s.controls.name.value)).toEqual([
      'gui',
      'gui-copy',
    ]);
    expect(editor()['expanded']()).toBe(1);
  });
});
