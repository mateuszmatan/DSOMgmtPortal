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

  it('keeps the agent labels within their column', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });
    const labels = form().controls.agentLabels;

    labels.setValue(Array.from({ length: 9 }, (_, i) => `${i}`.padEnd(100, 'a')).join(' '));
    expect(labels.hasError('columnLength')).toBe(false);
    expect(labels.hasError('maxItems')).toBe(false);

    labels.setValue(Array.from({ length: 20 }, (_, i) => `${i}`.padEnd(60, 'a')).join(' '));
    expect(labels.errors).toEqual({ columnLength: { max: 1000 } });
  });

  it('offers only the types the service has no pipeline of', async () => {
    await render({ service: servicePipelines({ pipelines: [pipeline()] }) });

    expect(dialog()['types'].map((type) => type.value)).not.toContain('FULL');
  });

  it('keeps the job of another pipeline type from blocking the save', async () => {
    await render({ service: servicePipelines({ pipelines: [] }) });
    const { type, extendedPipelineJob, securityPipelineJob } = form().controls;
    expect(type.value).toBe('FULL');
    expect(extendedPipelineJob.disabled && securityPipelineJob.disabled).toBe(true);

    type.setValue('SECURITY');
    extendedPipelineJob.setValue('x'.repeat(501));
    expect(form().invalid).toBe(true);

    type.setValue('EXTENDED');
    securityPipelineJob.setValue('CERT/gui-security');
    expect(extendedPipelineJob.disabled).toBe(true);
    expect(form().valid).toBe(true);

    dialog()['save']();
    const request = http.expectOne({ method: 'POST', url: '/api/services/10/pipelines' });
    expect(request.request.body).toMatchObject({
      type: 'EXTENDED',
      extendedPipelineJob: null,
      securityPipelineJob: 'CERT/gui-security',
    });
    request.flush(pipeline({ type: 'EXTENDED' }));
    expect(close).toHaveBeenCalled();
  });

  it('keeps the type of a stored pipeline and shows only its own job field', async () => {
    const stored = pipeline({ type: 'SECURITY', extendedPipelineJob: 'CERT/gui-extended' });
    await render({ service: servicePipelines({ pipelines: [stored] }), pipeline: stored });

    expect(form().controls.type.disabled).toBe(true);
    expect(form().controls.extendedPipelineJob.enabled).toBe(true);
    expect(form().controls.securityPipelineJob.disabled).toBe(true);
  });
});
