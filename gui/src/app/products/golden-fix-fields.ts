import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { GoldenFixPolicy } from '../core/models';
import { Field, Fields } from '../shared/fields';
import {
  GOLDEN_FIX_ECOSYSTEMS,
  GlobalGoldenFixForm,
  ServiceGoldenFixForm,
} from './product-form-model';

const COMMANDS: Field[] = [
  { key: 'verifyMavenCommand', label: 'Maven command', code: 'verify.commands.maven' },
  { key: 'verifyGradleCommand', label: 'Gradle command', code: 'verify.commands.gradle' },
  { key: 'verifyNpmCommand', label: 'npm command', span: 4, code: 'verify.commands.npm' },
  { key: 'verifyPipCommand', label: 'pip command', span: 4, code: 'verify.commands.pip' },
  { key: 'verifyPubCommand', label: 'pub command', span: 4, code: 'verify.commands.pub' },
].map((field) => ({ ...field, mono: true, placeholder: 'Library default' }));

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
      {
        key: 'ecosystems',
        kind: 'select',
        label: 'Ecosystems',
        span: 5,
        code: 'goldenFix.ecosystems',
        hint: this.global(g?.ecosystems),
        options: GOLDEN_FIX_ECOSYSTEMS.map((value) => ({ value, label: value })),
        multiple: true,
      },
      {
        key: 'minThreatLevel',
        kind: 'number',
        label: 'Minimum threat level',
        span: 3,
        min: 1,
        max: 10,
        code: 'minThreatLevel',
        hint: this.global(g?.minThreatLevel),
      },
      this.complete()
        ? {
            key: 'onlyDirectDependencies',
            kind: 'check',
            span: 4,
            label: 'Direct dependencies only',
            code: 'onlyDirectDependencies',
          }
        : {
            key: 'onlyDirectDependencies',
            kind: 'select',
            label: 'Dependencies',
            span: 4,
            code: 'onlyDirectDependencies',
            hint: this.global(g?.onlyDirectDependencies),
            options: [
              { value: null, label: 'Global value' },
              { value: true, label: 'Direct dependencies only' },
              { value: false, label: 'Direct and transitive' },
            ],
          },
      {
        key: 'goldenVersionTypes',
        kind: 'area',
        label: 'Golden version types',
        mono: true,
        placeholder: 'recommended-non-breaking',
        code: 'goldenVersionTypes',
        hint:
          'Nexus IQ remediation types, one per line, in order of preference' +
          this.global(g?.goldenVersionTypes, ' · '),
      },
      {
        key: 'excludeDirs',
        kind: 'area',
        label: 'Excluded folders',
        mono: true,
        code: 'excludeDirs',
        hint: 'one per line' + this.global(g?.excludeDirs, ' · '),
      },
    ];
  }

  protected verifyFields(): Field[] {
    const g = this.inherited();
    return [
      this.complete()
        ? {
            key: 'verifyEnabled',
            kind: 'check',
            span: 4,
            label: 'Build the fix before opening the pull request',
            code: 'verify.enabled',
          }
        : {
            key: 'verifyEnabled',
            kind: 'select',
            label: 'Verification',
            span: 4,
            code: 'verify.enabled',
            hint: this.global(g?.verifyEnabled),
            options: [
              { value: null, label: 'Global value' },
              { value: true, label: 'Build the fix first' },
              { value: false, label: 'Do not build it' },
            ],
          },
      {
        key: 'verifyMaxAttempts',
        kind: 'number',
        label: 'Attempts',
        span: 4,
        min: 1,
        max: 10,
        code: 'verify.maxAttempts',
        hint: this.global(g?.verifyMaxAttempts),
      },
      {
        key: 'verifyTimeoutMinutes',
        kind: 'number',
        label: 'Timeout (minutes)',
        span: 4,
        min: 1,
        max: 240,
        code: 'verify.timeoutMinutes',
        hint: this.global(g?.verifyTimeoutMinutes),
      },
    ];
  }

  protected commitFields(): Field[] {
    const g = this.inherited();
    return [
      {
        key: 'commitAuthorName',
        label: 'Author name',
        span: 4,
        code: 'commitAuthorName',
        hint: this.global(g?.commitAuthorName),
      },
      {
        key: 'commitAuthorEmail',
        label: 'Author e-mail',
        span: 5,
        code: 'commitAuthorEmail',
        hint: this.global(g?.commitAuthorEmail),
      },
      {
        key: 'timeZone',
        label: 'Time zone',
        span: 3,
        mono: true,
        placeholder: 'Europe/Warsaw',
        code: 'timeZone',
        hint: this.global(g?.timeZone),
        error: 'Must be a time zone ID such as Europe/Warsaw or UTC',
      },
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
