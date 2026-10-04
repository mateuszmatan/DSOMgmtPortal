import { product, service } from '../testing/fixtures';
import {
  applyFieldProblems,
  controlAt,
  createProductForm,
  createServiceForm,
  duplicateService,
  firstServiceWithProblem,
  patchProduct,
  toProductRequest,
  toServiceRequest,
} from './product-form-model';

describe('createServiceForm', () => {
  it('starts a new service with the defaults of the DevSecOps library', () => {
    const value = createServiceForm().getRawValue();

    expect(value.build).toEqual({ tool: 'GRADLE', sourceDir: '.', javaPath: '', autoSetup: false });
    expect(value.deployment.target).toBe('VM');
    expect(value.scm.goldenFixEnabled).toBe(true);
    expect(value.metrics).toEqual({ enabled: true, influxProject: '', influxEnv: 'test' });
  });

  it('needs the JDK path unless the build tool is Flutter or is set up automatically', () => {
    const form = createServiceForm();
    const javaPath = form.controls.build.controls.javaPath;
    expect(javaPath.hasError('required')).toBe(true);

    form.controls.build.controls.tool.setValue('FLUTTER');
    expect(javaPath.valid).toBe(true);

    form.controls.build.controls.tool.setValue('MAVEN');
    expect(javaPath.hasError('required')).toBe(true);

    form.controls.build.controls.autoSetup.setValue(true);
    expect(javaPath.valid).toBe(true);
  });

  it('needs the application and artifact names only for OpenShift', () => {
    const form = createServiceForm();
    const { appName, artifactName, target } = form.controls.deployment.controls;
    expect(appName.valid && artifactName.valid).toBe(true);

    target.setValue('OPENSHIFT');
    expect(appName.hasError('required')).toBe(true);
    expect(artifactName.hasError('required')).toBe(true);
  });

  it('needs a DAST target URL once DAST is switched on', () => {
    const form = createServiceForm();
    const { dastEnabled, dastTargetUrl } = form.controls.appScan.controls;
    expect(dastTargetUrl.valid).toBe(true);

    dastEnabled.setValue(true);
    expect(dastTargetUrl.hasError('required')).toBe(true);

    dastTargetUrl.setValue('ftp://host');
    expect(dastTargetUrl.hasError('pattern')).toBe(true);

    dastTargetUrl.setValue('https://cert-uat.testbbh.com');
    expect(dastTargetUrl.valid).toBe(true);
  });

  it('checks names, the AppScan application id and the number of scan patterns', () => {
    const form = createServiceForm({ name: 'Backend API' });
    expect(form.controls.name.hasError('pattern')).toBe(true);
    expect(form.controls.appScan.controls.applicationId.hasError('required')).toBe(true);

    form.controls.appScan.controls.applicationId.setValue(' 109f44ac-cc06-4ca0-884e-d944904f7019 ');
    expect(form.controls.appScan.controls.applicationId.valid).toBe(true);

    form.controls.nexusIq.controls.scanPatterns.setValue(
      Array.from({ length: 21 }, (_, i) => `p${i}`).join('\n'),
    );
    expect(form.controls.nexusIq.controls.scanPatterns.hasError('maxLines')).toBe(true);
  });
});

describe('toServiceRequest', () => {
  it('trims values, sends blanks as null and splits the scan patterns into distinct lines', () => {
    const form = createServiceForm(service());
    form.patchValue({
      description: '  ',
      nexusIq: { scanPatterns: ' **/*.jar \n\n**/*.war\n**/*.jar' },
      additionalConfig: { yaml: 'tests:\n  smoke: true\n' },
    });

    const request = toServiceRequest(form);

    expect(request.description).toBeNull();
    expect(request.nexusIq.scanPatterns).toEqual(['**/*.jar', '**/*.war']);
    expect(request.additionalConfig.yaml).toBe('tests:\n  smoke: true');
    expect(request.id).toBe(10);
  });

  it('drops the OpenShift names when the service deploys to a virtual machine', () => {
    const form = createServiceForm(
      service({ deployment: { target: 'OPENSHIFT', appName: 'gui', artifactName: 'gui.jar' } }),
    );
    expect(toServiceRequest(form).deployment).toEqual({
      target: 'OPENSHIFT',
      appName: 'gui',
      artifactName: 'gui.jar',
    });

    form.controls.deployment.controls.target.setValue('VM');
    expect(toServiceRequest(form).deployment).toEqual({
      target: 'VM',
      appName: null,
      artifactName: null,
    });
  });
});

describe('product form', () => {
  it('round-trips a stored product into the request the API takes', () => {
    const form = createProductForm();
    patchProduct(
      form,
      product({ services: [service(), service({ id: 11, name: 'backend-api' })] }),
    );

    const request = toProductRequest(form, 3);

    expect(form.valid).toBe(true);
    expect(request).toMatchObject({
      code: 'CERT',
      name: 'CertScanner',
      ownerTeam: 'Technology Architecture',
      appScan: { keyId: 'bbh_key', secretCredentialsId: 'hcl-app-scan-account' },
      version: 3,
    });
    expect(request.services.map((s) => [s.id, s.name])).toEqual([
      [10, 'gui'],
      [11, 'backend-api'],
    ]);
  });

  it('replaces the services of an earlier product when patched again', () => {
    const form = createProductForm();
    patchProduct(form, product({ services: [service(), service({ id: 11, name: 'api' })] }));
    patchProduct(
      form,
      product({ description: null, services: [service({ id: 12, name: 'worker' })] }),
    );

    expect(form.controls.services.length).toBe(1);
    expect(form.controls.description.value).toBe('');
  });

  it('duplicates a service as a new one without the values that must stay unique', () => {
    const copy = duplicateService(createServiceForm(service()));
    const value = copy.getRawValue();

    expect(value.id).toBeNull();
    expect(value.name).toBe('gui-copy');
    expect(value.sonar).toEqual({ projectName: 'CertScanner GUI', projectKey: '' });
    expect(value.metrics.influxProject).toBe('');
    expect(value.build.javaPath).toBe('/usr/lib/jvm/java-17-openjdk');
  });
});

describe('field problems reported by the API', () => {
  function formWithServices() {
    const form = createProductForm();
    patchProduct(form, product({ services: [service(), service({ id: 11, name: 'api' })] }));
    return form;
  }

  it('finds the control a field path names', () => {
    const form = formWithServices();
    expect(controlAt(form, 'services[1].build.javaPath')).toBe(
      form.controls.services.at(1).controls.build.controls.javaPath,
    );
    expect(controlAt(form, 'appScan.keyId')).toBe(form.controls.appScan.controls.keyId);
  });

  it('falls back to the nearest control above a path without one', () => {
    const form = formWithServices();
    expect(controlAt(form, 'services[0].nexusIq.scanPatterns[3]')).toBe(
      form.controls.services.at(0).controls.nexusIq.controls.scanPatterns,
    );
    expect(controlAt(form, 'unknown.field')).toBeNull();
  });

  it('shows each problem on its control and returns those without one', () => {
    const form = formWithServices();
    const unmatched = applyFieldProblems(form, [
      { field: 'services[1].name', message: 'is already used by another service' },
      { field: 'version', message: 'is stale' },
    ]);

    const name = form.controls.services.at(1).controls.name;
    expect(name.errors).toEqual({ server: 'is already used by another service' });
    expect(name.touched).toBe(true);
    expect(unmatched).toEqual([{ field: 'version', message: 'is stale' }]);

    name.setValue('api-2');
    expect(name.valid).toBe(true);
  });

  it('opens the first service holding a problem', () => {
    expect(
      firstServiceWithProblem([
        { field: 'name', message: 'x' },
        { field: 'services[3].name', message: 'x' },
        { field: 'services[1].sonar.projectKey', message: 'x' },
      ]),
    ).toBe(1);
    expect(firstServiceWithProblem([{ field: 'code', message: 'x' }])).toBeNull();
  });
});
