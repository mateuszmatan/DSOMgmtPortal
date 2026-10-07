import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import {
  anotherService,
  department,
  globalSettings,
  product,
  service,
  servicePipelines,
} from '../testing/fixtures';
import { buttonOf, inputOf } from '../testing/dom';
import { GeneratedKeys } from './generated-keys';
import { ProductEditor } from './product-editor';
import { ProductNameDialog } from './product-name-dialog';

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

  const naming = (name: string | undefined, departmentId = 3) =>
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(name && { name, departmentId }),
    } as unknown as MatDialogRef<unknown>);
  const departments = [department(), department({ id: 5, name: 'Fund Services' })];

  const suggestion = (name: string) =>
    http.expectOne(
      (request) =>
        request.url === '/api/products/code-suggestion' && request.params.get('name') === name,
    );

  async function start(name = 'CertScanner', code = 'CERTSCANNER') {
    naming(name);
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/settings').flush(globalSettings());
    suggestion(name).flush({ code });
    await fixture.whenStable();
  }

  async function rename(name: string) {
    editor()['form'].controls.name.setValue(name);
    await new Promise((resolve) => setTimeout(resolve, 350));
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  async function edit(services = [service(), anotherService()], stored = product({ services })) {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http.expectOne('/api/products/1').flush(stored);
    http
      .expectOne('/api/products/1/pipelines')
      .flush([
        servicePipelines(),
        servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [] }),
      ]);
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  const names = () =>
    [...page().querySelectorAll('mat-expansion-panel .service-name')].map((name) =>
      name.textContent?.trim(),
    );
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

  it('asks for the department and the name first and makes the unique code from the name', async () => {
    const open = naming('Payments Hub', 5);
    fixture.componentRef.setInput('department', '5');
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/settings').flush(globalSettings());
    suggestion('Payments Hub').flush({ code: 'PAYMENTSHUB2' });
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(ProductNameDialog, {
      data: { departments, departmentId: 5 },
    });
    expect(inputOf(page(), 'Name').value).toBe('Payments Hub');
    expect(inputOf(page(), 'Code').value).toBe('PAYMENTSHUB2');
    expect(editor()['form'].controls.departmentId.value).toBe(5);
    expect(page().querySelector('.product-fields mat-select')?.textContent).toContain(
      'Fund Services',
    );
    expect(editor().hasUnsavedChanges()).toBe(false);
  });

  it('goes back to the products when the name is not given', async () => {
    const open = naming(undefined);
    await fixture.whenStable();
    http.expectOne('/api/departments').flush(departments);

    expect(open.mock.calls[0][1]?.data).toEqual({ departments, departmentId: null });
    expect(router.navigate).toHaveBeenCalledWith(['/products']);
  });

  it('says why a new product cannot start without the departments', async () => {
    const open = naming('CertScanner');
    await fixture.whenStable();
    http
      .expectOne('/api/departments')
      .flush({ detail: 'The database is not available' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(open).not.toHaveBeenCalled();
    expect(page().querySelector('.banner')?.textContent).toBe('The database is not available');
  });

  it('keeps the code in step with the name until the code is changed by hand', async () => {
    await start();
    await rename('Cert Scanner Next');
    suggestion('Cert Scanner Next').flush({ code: 'CERTSCANNERNEXT' });
    await fixture.whenStable();

    expect(editor()['form'].controls.code.value).toBe('CERTSCANNERNEXT');

    editor()['form'].controls.code.setValue('CERTNEXT');
    await rename('Cert Scanner Two');

    http.expectNone((request) => request.url === '/api/products/code-suggestion');
    expect(editor()['form'].controls.code.value).toBe('CERTNEXT');
  });

  it('adds the product and opens it', async () => {
    await start();
    fillValidProduct();
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/products' });
    expect(request.request.body).toMatchObject({
      code: 'CERT',
      departmentId: 3,
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

  it('loads a stored product and saves it with its version', async () => {
    fixture.componentRef.setInput('id', '1');
    await fixture.whenStable();
    http
      .expectOne('/api/products/1')
      .flush(product({ services: [service(), anotherService({ name: 'api' })] }));
    http.expectOne('/api/products/1/pipelines').flush([servicePipelines()]);
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/departments').flush(departments);
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

  it('requires a department for a product that is not in one yet', async () => {
    await edit([service()], product({ departmentId: null }));

    await submit();

    http.expectNone({ method: 'PUT', url: '/api/products/1' });
    expect(page().querySelector('.save-error')?.textContent).toBe(
      'Some fields need your attention.',
    );
    expect(editor()['form'].controls.departmentId.hasError('required')).toBe(true);

    editor()['form'].controls.departmentId.setValue(5);
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.body.departmentId).toBe(5);
    request.flush(product({ departmentId: 5 }));
    await fixture.whenStable();
  });

  it('moves services up and down and keeps the open one open', async () => {
    await edit();
    editor()['panelToggled'](0, true);
    await fixture.whenStable();

    buttonOf(page(), 'Move gui down').click();
    await fixture.whenStable();
    expect(names()).toEqual(['api', 'gui']);
    expect(editor()['expanded']()).toBe(1);

    buttonOf(page(), 'Move gui up').click();
    await fixture.whenStable();
    expect(names()).toEqual(['gui', 'api']);
    expect(editor()['expanded']()).toBe(0);
    expect(buttonOf(page(), 'Move gui up').disabled).toBe(true);
    expect(editor().hasUnsavedChanges()).toBe(true);
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

  it('turns the product code into upper case as it is typed', async () => {
    await start();
    const input = inputOf(page(), 'Code');

    input.value = 'cert';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(input.value).toBe('CERT');
    expect(input.classList).not.toContain('uppercase');
    expect(editor()['form'].controls.code.valid).toBe(true);
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
