import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  OnInit,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { FieldProblem, GlobalSettings, Scanner, SCANNERS } from '../core/models';
import { Notifier } from '../core/notifier';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { GoldenFixFields } from '../products/golden-fix-fields';
import { CodeDialog, CodeDialogData } from '../shared/code-dialog';
import { Fields, chips } from '../shared/fields';
import { applyFieldProblems, revalidateAll } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { RelativeTimePipe } from '../shared/formatting';
import {
  GOLDEN_FIX_ENABLED,
  LIMIT_FIELDS,
  RELEASE_GATE_FIELDS,
  SETTINGS_PAGE,
} from './settings-fields';
import {
  SettingsSectionId,
  createSettingsForm,
  firstInvalidSection,
  patchSettings,
  toSettingsRequest,
} from './settings-form-model';

const SCANNER_INFO: Record<
  Scanner,
  { label: string; tool: string; gateKey: string; path: string }
> = {
  SAST: { label: 'SAST', tool: 'HCL AppScan static analysis', gateKey: 'sast', path: 'sast' },
  SCA: { label: 'SCA', tool: 'HCL AppScan open source analysis', gateKey: 'sca', path: 'sca' },
  NEXUS_IQ: {
    label: 'Nexus IQ',
    tool: 'Sonatype Nexus IQ policy evaluation',
    gateKey: 'niq',
    path: 'tools.nexusIq',
  },
  DAST: { label: 'DAST', tool: 'HCL AppScan dynamic analysis', gateKey: 'dast', path: 'dast' },
};

@Component({
  selector: 'dso-global-settings',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    Fields,
    GoldenFixFields,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './global-settings.html',
  styleUrl: './global-settings.scss',
})
export class GlobalSettingsPage implements OnInit, HasUnsavedChanges {
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(Notifier);
  private readonly dialog = inject(MatDialog);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = createSettingsForm();
  protected readonly settings = signal<GlobalSettings | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflict = signal(false);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);
  protected readonly submitted = signal(false);
  protected readonly loadingConfig = signal(false);
  protected readonly formEvent = toSignal(this.form.events);

  protected readonly errorText = errorText;
  protected readonly scanners = SCANNERS;
  protected readonly scannerInfo = SCANNER_INFO;
  protected readonly limitFields = LIMIT_FIELDS;
  protected readonly releaseGateFields = RELEASE_GATE_FIELDS;
  protected readonly goldenFixEnabled = GOLDEN_FIX_ENABLED;
  protected readonly note = chips;

  ngOnInit(): void {
    this.load();
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  protected sections() {
    this.formEvent();
    return SETTINGS_PAGE.map((section) => {
      const group = this.form.controls[section.id];
      return { ...section, problem: group.invalid && (this.submitted() || group.touched) };
    });
  }

  protected groupOf(id: SettingsSectionId) {
    return this.form.controls[id];
  }

  protected gateHint(): string {
    const keys = SCANNERS.map((scanner) => `\`${SCANNER_INFO[scanner].gateKey}\``).join(', ');
    return chips(`\`releaseGate.scanners\` · written as ${keys}`);
  }

  protected scrollTo(id: SettingsSectionId): void {
    document
      .getElementById(`settings-${id}`)
      ?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }

  protected save(): void {
    this.submitted.set(true);
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
    revalidateAll(this.form);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.saveError.set('Some fields need your attention.');
      this.revealProblem();
      return;
    }
    const request = toSettingsRequest(this.form, this.settings()?.version ?? null);
    this.saving.set(true);
    this.api
      .update(request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (settings) => {
          this.apply(settings);
          this.notifier.success('The DSOEnhanced library defaults are saved');
        },
        error: (error) => this.showSaveError(error),
      });
  }

  protected discard(): void {
    const settings = this.settings();
    if (settings) {
      this.apply(settings);
    }
  }

  protected showConfig(): void {
    this.loadingConfig.set(true);
    this.api
      .config()
      .pipe(
        finalize(() => this.loadingConfig.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (code) =>
          this.dialog.open<CodeDialog, CodeDialogData>(CodeDialog, {
            width: '880px',
            maxWidth: '95vw',
            data: {
              title: 'Generated global configuration',
              subtitle: this.form.dirty
                ? 'The platform and defaults sections every pipeline receives, from the saved settings. ' +
                  'Your unsaved changes are not included.'
                : 'The platform and defaults sections every pipeline receives with its configuration.',
              code,
              fileName: 'devsecops-global.yaml',
            },
          }),
        error: (error) => this.notifier.error(error),
      });
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .get()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (settings) => this.apply(settings),
        error: (error) => this.loadError.set(errorMessage(error)),
      });
  }

  private apply(settings: GlobalSettings): void {
    patchSettings(this.form, settings);
    this.settings.set(settings);
    this.submitted.set(false);
    this.saveError.set(null);
    this.conflict.set(false);
    this.unmatchedProblems.set([]);
  }

  private showSaveError(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.conflict.set(true);
      this.saveError.set('Not saved: the settings were changed by someone else.');
      afterNextRender(
        () =>
          document
            .querySelector('.banner.conflict')
            ?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }),
        { injector: this.injector },
      );
      return;
    }
    const problems = fieldProblems(error);
    if (problems.length === 0) {
      this.saveError.set(errorMessage(error));
      return;
    }
    this.unmatchedProblems.set(applyFieldProblems(this.form, problems));
    this.saveError.set('The portal did not accept some values. They are marked below.');
    this.revealProblem();
  }

  private revealProblem(): void {
    const section = firstInvalidSection(this.form);
    afterNextRender(
      () => {
        const target = section && document.getElementById(`settings-${section}`);
        const field =
          target?.querySelector('.mat-form-field-invalid, .field-error') ??
          (this.unmatchedProblems().length ? document.querySelector('.settings .problems') : null);
        (field ?? target)?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      },
      { injector: this.injector },
    );
  }
}
