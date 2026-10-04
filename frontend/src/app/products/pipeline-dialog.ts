import { COMMA, ENTER, SPACE } from '@angular/cdk/keycodes';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
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
import { errorText } from '../shared/form-errors';
import { applyFieldProblems } from './product-form-model';

export interface PipelineDialogData {
  service: ServicePipelines;
  /** The pipeline to change; absent when a pipeline is added. */
  pipeline?: Pipeline;
}

export const AGENT_LABEL = /^[A-Za-z0-9._-]{1,100}$/;
/** A Jenkins job path such as DevSecOps/CertScanner-gui, or the job's URL. */
export const JENKINS_JOB = /^(https?:\/\/\S+|[^\s:?#][^:?#]*)$/;

/** Adds a pipeline to a service or changes the settings of one; the result is the saved pipeline. */
@Component({
  selector: 'dso-pipeline-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
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

  protected readonly separators = [ENTER, COMMA, SPACE];
  protected readonly editing = this.data.pipeline !== undefined;
  /** Types the service has no pipeline of yet; a service has at most one pipeline of each type. */
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
    agentLabels: new FormControl<string[]>(this.data.pipeline?.agentLabels ?? ['linux-agent'], {
      nonNullable: true,
      validators: [
        Validators.required,
        (control) => (control.value.length > 20 ? { maxItems: true } : null),
      ],
    }),
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
  protected readonly labelError = signal<string | null>(null);
  protected readonly errorText = errorText;

  protected addLabel(event: MatChipInputEvent): void {
    const label = event.value.trim();
    if (!label) {
      return;
    }
    if (!AGENT_LABEL.test(label)) {
      this.labelError.set(
        `"${label}" is not a Jenkins label: use letters, digits, '.', '-' or '_'`,
      );
      return;
    }
    const labels = this.form.controls.agentLabels.value;
    if (!labels.includes(label)) {
      this.form.controls.agentLabels.setValue([...labels, label]);
    }
    this.form.controls.agentLabels.markAsTouched();
    this.labelError.set(null);
    event.chipInput.clear();
  }

  protected removeLabel(label: string): void {
    this.form.controls.agentLabels.setValue(
      this.form.controls.agentLabels.value.filter((value) => value !== label),
    );
    this.form.controls.agentLabels.markAsTouched();
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const request: PipelineRequest = {
      type: value.type,
      agentLabels: value.agentLabels,
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
