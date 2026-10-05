import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import {
  PIPELINE_TYPES,
  Pipeline,
  PipelineRequest,
  PipelineType,
  ServicePipelines,
} from '../core/models';
import {
  eachItem,
  fitsColumn,
  joinWords,
  maxWords,
  setEnabled,
  text,
  words,
} from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { applyFieldProblems } from './product-form-model';

export interface PipelineDialogData {
  service: ServicePipelines;
  pipeline?: Pipeline;
}

export const AGENT_LABEL = /^[A-Za-z0-9._-]{1,100}$/;
export const JENKINS_JOB = /^(https?:\/\/\S+|[^\s:?#][^:?#]*)$/;

@Component({
  selector: 'dso-pipeline-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-dialog.html',
  styleUrl: './pipeline-dialog.scss',
})
export class PipelineDialog {
  protected readonly data = inject<PipelineDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<PipelineDialog, Pipeline>>(MatDialogRef);
  private readonly api = inject(PipelinesApi);

  protected readonly editing = this.data.pipeline !== undefined;
  protected readonly types = PIPELINE_TYPES.filter(
    (type) =>
      type.value === this.data.pipeline?.type ||
      !this.data.service.pipelines.some((p) => p.type === type.value),
  );

  protected readonly form = new FormGroup({
    type: new FormControl<PipelineType>(
      { value: this.data.pipeline?.type ?? this.types[0]?.value ?? 'FULL', disabled: this.editing },
      { nonNullable: true },
    ),
    agentLabels: text(
      joinWords(this.data.pipeline?.agentLabels ?? ['linux-agent']),
      Validators.required,
      maxWords(20),
      eachItem(words, AGENT_LABEL, "Use letters, digits, '.', '-' or '_' in a Jenkins label"),
      fitsColumn(words, ',', 1000),
    ),
    jenkinsJob: new FormControl(this.data.pipeline?.jenkinsJob ?? '', {
      nonNullable: true,
      validators: [Validators.pattern(JENKINS_JOB), Validators.maxLength(1000)],
    }),
    extendedPipelineJob: new FormControl(this.data.pipeline?.extendedPipelineJob ?? '', {
      nonNullable: true,
      validators: Validators.maxLength(500),
    }),
    securityPipelineJob: new FormControl(this.data.pipeline?.securityPipelineJob ?? '', {
      nonNullable: true,
      validators: Validators.maxLength(500),
    }),
    description: new FormControl(this.data.pipeline?.description ?? '', {
      nonNullable: true,
      validators: Validators.maxLength(1000),
    }),
  });

  private readonly type = toSignal(this.form.controls.type.valueChanges, {
    initialValue: this.form.controls.type.value,
  });
  protected readonly selectedType = computed(() =>
    PIPELINE_TYPES.find((type) => type.value === this.type()),
  );
  protected readonly saving = signal(false);

  protected readonly error = signal<string | null>(null);
  protected readonly errorText = errorText;

  constructor() {
    const sync = (type: PipelineType) => {
      setEnabled(this.form.controls.extendedPipelineJob, type === 'SECURITY');
      setEnabled(this.form.controls.securityPipelineJob, type === 'EXTENDED');
    };
    this.form.controls.type.valueChanges.subscribe(sync);
    sync(this.form.controls.type.value);
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const request: PipelineRequest = {
      type: value.type,
      agentLabels: words(value.agentLabels),
      extendedPipelineJob:
        value.type === 'SECURITY' ? value.extendedPipelineJob.trim() || null : null,
      securityPipelineJob:
        value.type === 'EXTENDED' ? value.securityPipelineJob.trim() || null : null,
      jenkinsJob: value.jenkinsJob.trim() || null,
      description: value.description.trim() || null,
    };
    const pipeline = this.data.pipeline;
    this.saving.set(true);
    this.error.set(null);
    (pipeline
      ? this.api.update(pipeline.id, request)
      : this.api.create(this.data.service.serviceId, request)
    )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error) => {
          const problems = fieldProblems(error);
          const unmatched = applyFieldProblems(this.form, problems);
          this.error.set(
            problems.length
              ? unmatched.map((problem) => problem.message).join('; ') || null
              : errorMessage(error),
          );
        },
      });
  }
}
