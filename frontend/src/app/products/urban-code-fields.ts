import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { GlobalSettings } from '../core/models';
import { errorText } from '../shared/form-errors';
import {
  ServiceForm,
  UrbanCodeApplicationForm,
  createUrbanCodeApplicationForm,
  createUrbanCodeComponentForm,
} from './product-form-model';

@Component({
  selector: 'dso-urban-code-fields',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSlideToggleModule,
    MatTooltipModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './urban-code-fields.html',
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    mat-slide-toggle {
      padding: 4px 0;
    }
    .component {
      position: relative;
      margin-top: 10px;
      padding: 14px 52px 6px 14px;
      border: 1px solid var(--dso-border);
      background: #fbfcfd;

      .remove {
        position: absolute;
        top: 6px;
        right: 6px;
      }
    }
    .add-component {
      margin-top: 10px;
    }
  `,
})
export class UrbanCodeFields {
  readonly form = input.required<ServiceForm>();
  readonly defaults = input<GlobalSettings | null>(null);

  protected readonly errorText = errorText;

  protected applications(): UrbanCodeApplicationForm[] {
    return this.form().controls.urbanCodeApplications.controls;
  }

  protected addApplication(): void {
    this.form().controls.urbanCodeApplications.push(createUrbanCodeApplicationForm());
    this.form().markAsDirty();
  }

  protected removeApplication(index: number): void {
    this.form().controls.urbanCodeApplications.removeAt(index);
    this.form().markAsDirty();
  }

  protected addComponent(application: UrbanCodeApplicationForm): void {
    application.controls.components.push(createUrbanCodeComponentForm());
    this.form().markAsDirty();
  }

  protected removeComponent(application: UrbanCodeApplicationForm, index: number): void {
    application.controls.components.removeAt(index);
    this.form().markAsDirty();
  }

  protected fallback(value: string | undefined): string {
    return value ? `left empty: ${value}` : 'left empty: the global default';
  }
}
