import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Pipeline } from '../core/models';
import { errorText } from '../shared/form-errors';

@Component({
  selector: 'dso-revoke-key-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Invalidate the pipeline key?</h2>
    <form [formGroup]="form" (ngSubmit)="revoke()" novalidate>
      <mat-dialog-content>
        <div class="banner">
          <span>
            The {{ pipeline.type.toLowerCase() }} pipeline of
            <strong class="mono">{{ pipeline.serviceName }}</strong>
            stops working: the portal refuses its configuration from now on. You can issue a new key
            later.
          </span>
        </div>
        <mat-form-field class="full-width">
          <mat-label>Reason</mat-label>
          <textarea
            matInput
            formControlName="reason"
            rows="3"
            placeholder="Service retired, key leaked in a build log, ..."
          ></textarea>
          <mat-hint>Kept in the key history and shown to the pipeline when it is refused</mat-hint>
          <mat-error>{{ errorText(reason) }}</mat-error>
        </mat-form-field>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" class="danger" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          }
          Invalidate key
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-dialog-content {
      width: min(520px, 80vw);
    }
    .banner {
      margin-bottom: 12px;
    }
    .danger {
      --mat-button-filled-container-color: var(--dso-danger);
    }
    mat-spinner {
      display: inline-block;
      margin-right: 8px;
      vertical-align: middle;
    }
  `,
})
export class RevokeKeyDialog {
  protected readonly pipeline = inject<Pipeline>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<RevokeKeyDialog, Pipeline>>(MatDialogRef);
  private readonly api = inject(PipelinesApi);

  protected readonly form = new FormGroup({
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(500)],
    }),
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
