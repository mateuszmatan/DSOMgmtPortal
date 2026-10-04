import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { GoldenFixPolicy } from '../core/models';
import { errorText } from '../shared/form-errors';
import { GOLDEN_FIX_ECOSYSTEMS, GoldenFixControls, ServiceGoldenFixForm } from './product-form-model';

/**
 * The fields of a GoldenFix policy ({@code goldenFix}). In the global settings every value is set; for a
 * service a blank field keeps the global value, which its hint shows.
 */
@Component({
  selector: 'dso-golden-fix-fields',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './golden-fix-fields.html',
  styles: `
    :host {
      display: contents;
    }
    code {
      font-size: 11.5px;
    }
    mat-slide-toggle {
      padding: 4px 0;
    }
  `,
})
export class GoldenFixFields {
  readonly group = input.required<FormGroup<GoldenFixControls> | ServiceGoldenFixForm>();
  /** The global policy a service falls back to; absent in the global settings themselves. */
  readonly inherited = input<GoldenFixPolicy | null>(null);
  /** Whether this is the global policy, where every value is required. */
  readonly complete = input(false);

  protected readonly ecosystems = GOLDEN_FIX_ECOSYSTEMS;
  protected readonly errorText = errorText;

  /** The hint of a field a service may leave blank. */
  protected global(value: string | number | boolean | readonly string[] | null | undefined): string {
    if (this.complete()) {
      return '';
    }
    if (value === null || value === undefined || (Array.isArray(value) && value.length === 0)) {
      return ' · left empty: global value';
    }
    const text = Array.isArray(value)
      ? value.join(', ')
      : typeof value === 'boolean'
        ? value
          ? 'yes'
          : 'no'
        : String(value);
    return ` · left empty: ${text}`;
  }
}
