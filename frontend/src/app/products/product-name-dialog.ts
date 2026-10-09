import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Department } from '../core/models';
import { filled, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';

export interface ProductNameDialogData {
  departments: readonly Department[];
  departmentId: number | null;
}

export interface NamedProduct {
  name: string;
  departmentId: number;
}

@Component({
  selector: 'dso-product-name-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Add product</h2>
    <form [formGroup]="form" (ngSubmit)="next()" novalidate>
      <mat-dialog-content>
        <p>
          Start with the product's department and name. The portal makes the product's unique code
          from the name.
        </p>
        <mat-form-field class="full-width">
          <mat-label>Department</mat-label>
          <mat-select formControlName="departmentId">
            @for (department of data.departments; track department.id) {
              <mat-option [value]="department.id">{{ department.name }}</mat-option>
            }
          </mat-select>
          <mat-error>{{ errorText(form.controls.departmentId) }}</mat-error>
        </mat-form-field>
        <mat-form-field class="full-width">
          <mat-label>Product name</mat-label>
          <input matInput formControlName="name" placeholder="CertScanner" required />
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
      display: flex;
      flex-direction: column;
      gap: 10px;
      width: min(420px, 80vw);
    }
    p {
      margin: 0;
    }
  `,
})
export class ProductNameDialog {
  protected readonly data = inject<ProductNameDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ProductNameDialog, NamedProduct>>(MatDialogRef);

  protected readonly form = new FormGroup({
    departmentId: new FormControl(
      this.data.departments.find((department) => department.id === this.data.departmentId)?.id ??
        null,
      Validators.required,
    ),
    name: text('', filled, max(200)),
  });
  protected readonly name = this.form.controls.name;
  protected readonly errorText = errorText;

  protected next(): void {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      this.dialogRef.close({
        name: this.name.value.trim(),
        departmentId: this.form.controls.departmentId.value!,
      });
    }
  }
}
