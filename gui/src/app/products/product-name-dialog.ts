import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { errorText } from '../shared/form-errors';

@Component({
  selector: 'dso-product-name-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Add product</h2>
    <form [formGroup]="form" (ngSubmit)="next()" novalidate>
      <mat-dialog-content>
        <p>Start with the product's name. The portal makes the product's unique code from it.</p>
        <mat-form-field class="full-width">
          <mat-label>Product name</mat-label>
          <input matInput formControlName="name" placeholder="CertScanner" />
          <mat-error>{{ errorText(name) }}</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit">Continue</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-dialog-content {
      width: min(420px, 80vw);
    }
  `,
})
export class ProductNameDialog {
  private readonly dialogRef = inject<MatDialogRef<ProductNameDialog, string>>(MatDialogRef);

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
  });
  protected readonly name = this.form.controls.name;
  protected readonly errorText = errorText;

  protected next(): void {
    this.name.markAsTouched();
    const name = this.name.value.trim();
    if (!name) {
      this.name.setErrors({ required: true });
    } else if (this.name.valid) {
      this.dialogRef.close(name);
    }
  }
}
