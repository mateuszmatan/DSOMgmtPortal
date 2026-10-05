import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { globalSettings, product, service, servicePipelines } from '../testing/fixtures';
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
      .flush(product({ services: [service(), service({ id: 11, name: 'api' })] }));
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
