import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { GoldenFixPolicy } from '../core/models';
import { Field, Fields, area, check, choice, count, line, mono } from '../shared/fields';
import {
  GOLDEN_FIX_ECOSYSTEMS,
  GlobalGoldenFixForm,
  ServiceGoldenFixForm,
} from './product-form-model';

const COMMANDS: Field[] = [
  mono('verifyMavenCommand', 'Maven command', 'verify.commands.maven'),
  mono('verifyGradleCommand', 'Gradle command', 'verify.commands.gradle'),
  mono('verifyNpmCommand', 'npm command', 'verify.commands.npm', 4),
  mono('verifyPipCommand', 'pip command', 'verify.commands.pip', 4),
  mono('verifyPubCommand', 'pub command', 'verify.commands.pub', 4),
].map((field) => ({ ...field, placeholder: 'Library default' }));

@Component({
  selector: 'dso-golden-fix-fields',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let g = group();
    <dso-fields [group]="g" [fields]="policyFields()" />
    <h5 class="sub-heading span-12">Verification build</h5>
    <dso-fields [group]="g" [fields]="verifyFields()" />
    <dso-fields [group]="g" [fields]="commands" />
    <h5 class="sub-heading span-12">Commits</h5>
    <dso-fields [group]="g" [fields]="commitFields()" />
  `,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class GoldenFixFields {
  readonly group = input.required<GlobalGoldenFixForm | ServiceGoldenFixForm>();
  readonly inherited = input<GoldenFixPolicy | null>(null);
  readonly complete = input(false);

  protected readonly commands = COMMANDS;

  protected policyFields(): Field[] {
    const g = this.inherited();
    return [
      choice(
        'ecosystems',
        'Ecosystems',
        GOLDEN_FIX_ECOSYSTEMS.map((value) => ({ value, label: value })),
        'goldenFix.ecosystems',
        5,
        { multiple: true, hint: this.global(g?.ecosystems) },
      ),
      count('minThreatLevel', 'Minimum threat level', 'minThreatLevel', 3, {
        min: 0,
        max: 10,
        hint: this.global(g?.minThreatLevel),
      }),
      this.complete()
        ? check('onlyDirectDependencies', 'Direct dependencies only', 'onlyDirectDependencies', 4)
        : choice(
            'onlyDirectDependencies',
            'Dependencies',
            [
              { value: null, label: 'Global value' },
              { value: true, label: 'Direct dependencies only' },
              { value: false, label: 'Direct and transitive' },
            ],
            'onlyDirectDependencies',
            4,
            { hint: this.global(g?.onlyDirectDependencies) },
          ),
      area('goldenVersionTypes', 'Golden version types', 'goldenVersionTypes', 6, {
        mono: true,
        placeholder: 'recommended-non-breaking',
        hint:
          'Nexus IQ remediation types, one per line, in order of preference' +
          this.global(g?.goldenVersionTypes, ' · '),
      }),
      area('excludeDirs', 'Excluded folders', 'excludeDirs', 6, {
        mono: true,
        hint: 'one per line' + this.global(g?.excludeDirs, ' · '),
      }),
    ];
  }

  protected verifyFields(): Field[] {
    const g = this.inherited();
    return [
      this.complete()
        ? check(
            'verifyEnabled',
            'Build the fix before opening the pull request',
            'verify.enabled',
            4,
          )
        : choice(
            'verifyEnabled',
            'Verification',
            [
              { value: null, label: 'Global value' },
              { value: true, label: 'Build the fix first' },
              { value: false, label: 'Do not build it' },
            ],
            'verify.enabled',
            4,
            { hint: this.global(g?.verifyEnabled) },
          ),
      count('verifyMaxAttempts', 'Attempts', 'verify.maxAttempts', 4, {
        min: 1,
        hint: this.global(g?.verifyMaxAttempts),
      }),
      count('verifyTimeoutMinutes', 'Timeout (minutes)', 'verify.timeoutMinutes', 4, {
        min: 1,
        hint: this.global(g?.verifyTimeoutMinutes),
      }),
    ];
  }

  protected commitFields(): Field[] {
    const g = this.inherited();
    return [
      line('commitAuthorName', 'Author name', 'commitAuthorName', 4, {
        hint: this.global(g?.commitAuthorName),
      }),
      line('commitAuthorEmail', 'Author e-mail', 'commitAuthorEmail', 5, {
        hint: this.global(g?.commitAuthorEmail),
      }),
      mono('timeZone', 'Time zone', 'timeZone', 3, {
        placeholder: 'Europe/Warsaw',
        hint: this.global(g?.timeZone),
        error: 'Must be a time zone ID such as Europe/Warsaw or UTC',
      }),
    ];
  }

  private global(
    value: string | number | boolean | readonly string[] | null | undefined,
    lead = '',
  ): string {
    if (this.complete()) {
      return '';
    }
    const text = Array.isArray(value)
      ? value.join(', ')
      : typeof value === 'boolean'
        ? value
          ? 'yes'
          : 'no'
        : (value ?? '');
    return `${lead}left empty: ${text === '' ? 'global value' : text}`;
  }
}
