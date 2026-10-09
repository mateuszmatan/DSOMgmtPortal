import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline, pipelineTypeName } from '../core/models';
import { filled, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { DIALOG } from '../ui/dialog';
import { FORM_FIELD } from '../ui/form-field';
import { DsoSpinner } from '../ui/loading';

@Component({
  selector: 'dso-revoke-key-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD, DsoSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>Invalidate the pipeline key?</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="revoke()" novalidate>
      <div class="modal-body">
        <div class="banner">
          <span>
            The {{ typeName }} pipeline of
            <strong class="mono">{{ pipeline.serviceName }}</strong>
            stops working: the portal refuses its configuration from now on. You can issue a new key
            later.
          </span>
        </div>
        <dso-form-field class="full-width">
          <dso-label>Reason</dso-label>
          <textarea
            dsoInput
            formControlName="reason"
            required
            rows="3"
            placeholder="Service retired, key leaked in a build log, ..."
          ></textarea>
          <dso-hint>Kept in the key history and shown to the pipeline when it is refused</dso-hint>
          <dso-error>{{ errorText(reason) }}</dso-error>
        </dso-form-field>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
        <button type="submit" class="btn btn-danger" [disabled]="saving()">
          @if (saving()) {
            <dso-spinner />
          }
          Invalidate key
        </button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      width: min(520px, 80vw);
    }
  `,
})
export class RevokeKeyDialog {
  protected readonly pipeline = inject<Pipeline>(DIALOG_DATA);
  protected readonly typeName = pipelineTypeName(this.pipeline.type);
  private readonly dialogRef = inject<DialogRef<Pipeline, RevokeKeyDialog>>(DialogRef);
  private readonly api = inject(PipelinesApi);

  protected readonly form = new FormGroup({
    reason: text('', filled, max(500)),
  });
  protected readonly reason = this.form.controls.reason;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorText = errorText;

  protected revoke(): void {
    this.reason.markAsTouched();
    if (this.reason.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .revokeKey(this.pipeline.id, this.reason.value.trim())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (pipeline) => this.dialogRef.close(pipeline),
        error: (error) => this.error.set(errorMessage(error)),
      });
  }
}
