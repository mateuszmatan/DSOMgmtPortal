import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { pipeline, servicePipelines } from '../testing/fixtures';
import { PipelineDialog, PipelineDialogData } from './pipeline-dialog';

describe('PipelineDialog', () => {
  let fixture: ComponentFixture<PipelineDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function render(data: PipelineDialogData) {
    TestBed.configureTestingModule({
      imports: [PipelineDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineDialog);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const dialog = () => fixture.componentInstance;
  const form = () => dialog()['form'];
  const page = () => fixture.nativeElement as HTMLElement;
  const submit = async () => {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  };
  const type = async (name: string, value: string) => {
    const input = page().querySelector<HTMLInputElement | HTMLTextAreaElement>(
      `[formControlName=${name}]`,
    )!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  };
  const labels = () =>
    [...page().querySelectorAll('mat-label')].map((label) => label.textContent?.trim());
  const every = () =>
    servicePipelines({
      pipelines: (['FULL', 'SECURITY', 'EXTENDED', 'SAST'] as const).map((value, index) =>
        pipeline({ id: 100 + index, type: value }),
      ),
    });

  it('offers only the types the service has no pipeline of', async () => {
    await render({ service: servicePipelines({ pipelines: [pipeline()] }) });

    expect(dialog()['types'].map((type) => type.value)).not.toContain('FULL');
  });

  it('keeps the type of a stored pipeline and shows only its own job field', async () => {
    const stored = pipeline({ type: 'SECURITY', extendedPipelineJob: 'CERT/gui-extended' });
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    expect(form().controls.type.disabled).toBe(true);
    expect(form().controls.extendedPipelineJob.enabled).toBe(true);
    expect(form().controls.securityPipelineJob.disabled).toBe(true);
  });

  it('starts a new pipeline with the default agent and explains its key', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });

    expect(page().querySelector('h2')?.textContent).toBe('Add pipeline');
    expect(page().querySelector('.intro')?.textContent).toContain('gets its own unique key');
    expect(page().querySelector<HTMLInputElement>('[formControlName=agentLabels]')?.value).toBe(
      'linux-agent',
    );
    expect(page().querySelector('mat-hint')?.textContent).toContain(
      'Build, scans, tests, deployment and release',
    );
    expect(labels()).not.toContain('Extended pipeline job');
    expect(labels()).not.toContain('Security pipeline job');
  });

  it('says when the service has a pipeline of every type and adds nothing', async () => {
    await render({ service: every() });

    expect(page().querySelector('.banner.info')?.textContent).toBe(
      'The service already has a pipeline of every type.',
    );
    expect(page().querySelector('mat-select')).toBeNull();
    expect(page().querySelector<HTMLButtonElement>('button[type=submit]')?.disabled).toBe(true);
  });

  it('shows the security pipeline field of an extended pipeline', async () => {
    const stored = pipeline({
      type: 'EXTENDED',
      securityPipelineJob: 'CERT/gui-security',
      description: 'Deploys to QC',
    });
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    expect(page().querySelector('h2')?.textContent).toBe('Pipeline settings');
    expect(labels()).toContain('Security pipeline job');
    expect(labels()).not.toContain('Extended pipeline job');
    expect(
      page().querySelector<HTMLInputElement>('[formControlName=securityPipelineJob]')?.value,
    ).toBe('CERT/gui-security');
    expect(page().querySelector('button[type=submit]')?.textContent?.trim()).toBe('Save');
  });

  it('saves the settings of a stored pipeline with trimmed values', async () => {
    const stored = pipeline();
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    await type('agentLabels', ' linux-agent,  docker ');
    await type('jenkinsJob', '');
    await type('description', '  Release build  ');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/pipelines/100' });
    expect(request.request.body).toEqual({
      type: 'FULL',
      agentLabels: ['linux-agent', 'docker'],
      extendedPipelineJob: null,
      securityPipelineJob: null,
      jenkinsJob: null,
      description: 'Release build',
    });
    expect(page().querySelector('mat-spinner')).not.toBeNull();
    await submit();
    http.expectNone({ method: 'PUT', url: '/api/pipelines/100' });

    request.flush(pipeline({ agentLabels: ['linux-agent', 'docker'] }));
    expect(close).toHaveBeenCalledWith(pipeline({ agentLabels: ['linux-agent', 'docker'] }));
  });

  it('refuses unusable agent labels and job paths before sending', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });

    await type('agentLabels', 'linux agent!');
    await type('jenkinsJob', 'DevSecOps/CERT?branch=main');
    await submit();

    http.expectNone('/api/services/10/pipelines');
    const errors = [...page().querySelectorAll('mat-error')].map((e) => e.textContent?.trim());
    expect(errors).toContain("Use letters, digits, '.', '-' or '_' in a Jenkins label: agent!");
    expect(errors).toContain(
      'A job path such as DevSecOps/CERT/backend-api-full, or an http or https URL',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('marks the fields the API refused and lists the problems without a field', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });

    await submit();
    http.expectOne('/api/services/10/pipelines').flush(
      {
        title: 'Bad Request',
        detail: 'The request has invalid values',
        errors: [
          { field: 'jenkinsJob', message: 'is used by the full pipeline of api' },
          { field: 'serviceId', message: 'the service was deleted' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(form().controls.jenkinsJob.errors).toEqual({
      server: 'is used by the full pipeline of api',
    });
    expect(page().querySelector('[role=alert]')?.textContent).toBe('the service was deleted');
    expect(page().querySelector('mat-spinner')).toBeNull();
    expect(close).not.toHaveBeenCalled();
  });
});
