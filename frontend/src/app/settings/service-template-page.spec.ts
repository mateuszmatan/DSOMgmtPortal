import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { buttonOf, inputOf, text } from '../testing/dom';
import { serviceTemplate } from '../testing/fixtures';
import {
  ServiceTemplatePage,
  createTemplateForm,
  patchTemplate,
  toTemplateRequest,
} from './service-template-page';

describe('the service template form', () => {
  it('sends the values as they are typed, empty ones as null and the agents as a list', () => {
    const form = createTemplateForm();
    patchTemplate(
      form,
      serviceTemplate({ healthCheckUrl: null, agentLabels: ['linux', 'docker'] }),
    );

    expect(form.controls.agentLabels.value).toBe('linux, docker');
    expect(form.controls.healthCheckUrl.value).toBe('');

    form.controls.jenkinsJob.setValue('  Teams/{CODE}/{service}  ');
    expect(toTemplateRequest(form, 3)).toMatchObject({
      agentLabels: ['linux', 'docker'],
      jenkinsJob: 'Teams/{CODE}/{service}',
      healthCheckUrl: null,
      openShiftProject: '{code}-{service}',
      version: 3,
    });
  });

  it('refuses placeholders the fields do not know', () => {
    const form = createTemplateForm();
    patchTemplate(form, serviceTemplate());

    form.controls.jenkinsJob.setValue('DevSecOps/{CODE}/{type}/{branch}');
    form.controls.openShiftProject.setValue('{code}-{type}');

    expect(form.controls.jenkinsJob.errors).toEqual({
      rule: 'Unknown placeholder {branch}: use {CODE}, {code}, {service}, {type}',
    });
    expect(form.controls.openShiftProject.errors).toEqual({
      rule: 'Unknown placeholder {type}: use {CODE}, {code}, {service}',
    });
  });
});

describe('ServiceTemplatePage', () => {
  let fixture: ComponentFixture<ServiceTemplatePage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ServiceTemplatePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ServiceTemplatePage);
  });

  afterEach(() => http.verify());

  const templatePage = () => fixture.componentInstance;
  const form = () => templatePage()['form'];
  const page = () => fixture.nativeElement as HTMLElement;
  const examples = () =>
    [...page().querySelectorAll('.example .pairs > div')].map((pair) => [
      text(pair.querySelector('dt')),
      text(pair.querySelector('dd')),
    ]);

  async function load(template = serviceTemplate({ version: 2 })) {
    await fixture.whenStable();
    http.expectOne({ method: 'GET', url: '/api/service-template' }).flush(template);
    await fixture.whenStable();
  }

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('shows the sections of the template and what a service gets from it', async () => {
    await load();

    expect(text(page().querySelector('.meta'))).toContain(
      'What Self-service and the Add service and Add pipeline forms fill in for a new service.',
    );
    expect(text(page().querySelector('.meta'))).toContain('Version 2');
    expect([...page().querySelectorAll('.section h2')].map(text)).toEqual([
      'Pipelines',
      'Build',
      'Nexus IQ and Bitbucket',
      'OpenShift',
      'Example',
    ]);
    expect(inputOf(page(), 'Jenkins job').value).toBe('DevSecOps/{CODE}/{service}-{type}');
    expect(examples()).toEqual([
      ['Full pipeline job', 'DevSecOps/CERT/backend-api-full'],
      ['Nexus IQ application', 'cert-backend-api'],
      ['Bitbucket repository', 'https://bitbucket.bbh.com/projects/CERT/repos/cert-backend-api'],
      ['OpenShift projects', 'cert-backend-api-build, cert-backend-api-rd, cert-backend-api-qc'],
    ]);
    expect(page().querySelector('mat-icon')).toBeNull();
    expect(templatePage().hasUnsavedChanges()).toBe(false);
  });

  it('says when the BBH defaults were never saved', async () => {
    await load(serviceTemplate({ version: null, updatedAt: null }));

    expect(text(page().querySelector('.meta'))).toContain('The BBH defaults, not saved yet.');
  });

  it('updates the example as the patterns are typed', async () => {
    await load();

    await type('Jenkins job', 'Teams/{code}/{service}/{type}');
    await type('OpenShift project', '');

    expect(examples()[0]).toEqual(['Full pipeline job', 'Teams/cert/backend-api/full']);
    expect(examples()[3]).toEqual(['OpenShift projects', 'none']);
    expect(text(page().querySelector('.save-bar'))).toContain('Unsaved changes');
    expect(templatePage().hasUnsavedChanges()).toBe(true);
  });

  it('saves the template with the version it was read at', async () => {
    await load();

    await type('Jenkins agent labels', 'linux, docker');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/service-template' });
    expect(request.request.body).toMatchObject({ agentLabels: ['linux', 'docker'], version: 2 });
    request.flush(serviceTemplate({ version: 3, agentLabels: ['linux', 'docker'] }));
    await fixture.whenStable();

    expect(text(page().querySelector('.meta'))).toContain('Version 3');
    expect(templatePage().hasUnsavedChanges()).toBe(false);
    expect([...document.querySelectorAll('mat-snack-bar-container')].map(text).join(' ')).toContain(
      'The service template is saved',
    );
  });

  it('sends nothing while a pattern is invalid', async () => {
    await load();

    await type('Nexus IQ application', '{code}/{service}');
    await type('Jenkins agent labels', '');
    await submit();

    http.expectNone({ method: 'PUT', url: '/api/service-template' });
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(form().controls.nexusIqApplication.invalid).toBe(true);
    expect(form().controls.agentLabels.invalid).toBe(true);
  });

  it('explains a concurrent change and reloads the current template', async () => {
    await load();
    await type('Health check path', '/health');
    await submit();

    http
      .expectOne({ method: 'PUT', url: '/api/service-template' })
      .flush({ detail: 'Stale' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(page().querySelector('.conflict'))).toContain(
      'Someone else saved the template after you opened this page.',
    );
    expect(page().querySelector<HTMLButtonElement>('button[type=submit]')?.disabled).toBe(true);

    buttonOf(page(), 'Reload').click();
    await load(serviceTemplate({ version: 4 }));

    expect(page().querySelector('.conflict')).toBeNull();
    expect(inputOf(page(), 'Health check path').value).toBe('/actuator/health');
  });

  it('marks the values the API refused and lists the problems without a field', async () => {
    await load();
    await type('OpenShift project', '{code}-{service}-team');
    await submit();

    http.expectOne({ method: 'PUT', url: '/api/service-template' }).flush(
      {
        detail: 'The request has invalid values',
        errors: [
          { field: 'openShiftProject', message: 'is too long once filled in' },
          { field: 'version', message: 'must not be negative' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(form().controls.openShiftProject.errors).toEqual({
      server: 'is too long once filled in',
    });
    expect(text(page().querySelector('.problems'))).toBe('version: must not be negative');
    expect(text(page().querySelector('.save-error'))).toBe(
      'The portal did not accept some values. They are marked above.',
    );
  });

  it('discards the changes made on the page', async () => {
    await load();
    await type('Maven goals', 'clean install');

    buttonOf(page(), 'Discard changes').click();
    await fixture.whenStable();

    expect(inputOf(page(), 'Maven goals').value).toBe('clean verify');
    expect(templatePage().hasUnsavedChanges()).toBe(false);
  });

  it('shows why the template cannot be read and reads it again on request', async () => {
    await fixture.whenStable();
    http
      .expectOne('/api/service-template')
      .flush({ detail: 'Database unavailable' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toBe('Database unavailable');
    expect(page().querySelector('form')).toBeNull();

    buttonOf(page(), 'Try again').click();
    await load();
    expect(page().querySelector('form')).not.toBeNull();
  });
});
