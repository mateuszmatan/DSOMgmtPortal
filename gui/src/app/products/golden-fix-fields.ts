import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { GoldenFixPolicy } from '../core/models';
import { errorText } from '../shared/form-errors';
import {
  GOLDEN_FIX_ECOSYSTEMS,
  GlobalGoldenFixForm,
  ServiceGoldenFixForm,
} from './product-form-model';

@Component({
  selector: 'dso-golden-fix-fields',
  imports: [
    ReactiveFormsModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
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
  `,
})
export class GoldenFixFields {
  readonly group = input.required<GlobalGoldenFixForm | ServiceGoldenFixForm>();
  readonly inherited = input<GoldenFixPolicy | null>(null);
  readonly complete = input(false);

  protected readonly ecosystems = GOLDEN_FIX_ECOSYSTEMS;
  protected readonly errorText = errorText;

  protected global(
    value: string | number | boolean | readonly string[] | null | undefined,
  ): string {
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
