import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { BuildTool } from '../core/models';
import { errorText } from '../shared/form-errors';
import { ADDITIONAL_CONFIG_KEYS, ServiceForm } from './product-form-model';

/**
 * The fields of one service, grouped like the sections of its config.yaml entry. It is checked with its parent
 * so that changes the parent makes to the form, such as errors the API reported, show up at once.
 */
@Component({
  selector: 'dso-service-fields',
  imports: [
    ReactiveFormsModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './service-fields.html',
  styleUrl: './service-fields.scss',
})
export class ServiceFields {
  readonly form = input.required<ServiceForm>();
  readonly productCode = input('');

  protected readonly buildTools: { value: BuildTool; label: string }[] = [
    { value: 'GRADLE', label: 'Gradle' },
    { value: 'MAVEN', label: 'Maven' },
    { value: 'FLUTTER', label: 'Flutter' },
  ];
  protected readonly additionalKeys = ADDITIONAL_CONFIG_KEYS.join(', ');
  protected readonly errorText = errorText;

  /** The metrics project tag the API fills in when none is given. */
  protected defaultProject(): string {
    return `${this.productCode() || 'CODE'}-${this.form().controls.name.value || 'service'}`;
  }
}
