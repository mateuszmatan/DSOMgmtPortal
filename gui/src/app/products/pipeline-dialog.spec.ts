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
});
