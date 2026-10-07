import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BuildTool, DeployTarget } from '../core/models';
import { SERVICE_NAME, UUID } from '../products/product-form-model';
import { filled, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { ChoiceTiles } from '../shared/choice-tiles';
import {
  OPENSHIFT_PROJECT,
  WizardPipeline,
  WizardService,
  TARGETS,
  TOOLS,
  choiceLabel,
} from './self-service-model';

export interface ServiceDialogData {
  pipeline: WizardPipeline;
  service: WizardService | null;
  takenNames: readonly string[];
}

const NAME_HELP = "Use letters, digits, '.', '-' or '_', starting with a letter or digit";
const APP_SCAN_HELP = 'Paste the ID as it is, for example 109f44ac-cc06-4ca0-884e-d944904f7019';
const PROJECT_HELP = "Use lower case letters, digits and '-', for example pay-payhub";

@Component({
  selector: 'dso-service-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ChoiceTiles,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.service ? 'Change ' + data.service.name : 'Add a service' }}</h2>
    <form [formGroup]="form" (ngSubmit)="next()" novalidate>
      <mat-dialog-content>
        @if (!existing) {
          <p class="page-count">
            Part {{ page() }} of 2 · {{ page() === 1 ? 'About the service' : 'Build and run' }}
          </p>
        }
        @if (page() === 1) {
          <mat-form-field class="full-width">
            <mat-label>Service name</mat-label>
            <input
              matInput
              formControlName="name"
              placeholder="backend-api"
              autocomplete="off"
              required
            />
            <mat-hint>A short name, for example gui or backend-api</mat-hint>
            <mat-error>{{ errorText(form.controls.name, nameHelp) }}</mat-error>
          </mat-form-field>
          <mat-form-field class="full-width">
            <mat-label>What it does</mat-label>
            <input matInput formControlName="description" placeholder="REST API of the product" />
            <mat-hint>Optional</mat-hint>
            <mat-error>{{ errorText(form.controls.description) }}</mat-error>
          </mat-form-field>
          <mat-form-field class="full-width">
            <mat-label>AppScan application ID</mat-label>
            <input
              matInput
              class="mono"
              formControlName="appScanId"
              placeholder="109f44ac-cc06-4ca0-884e-d944904f7019"
              autocomplete="off"
              required
            />
            <mat-hint>The Application Security team gives it to you</mat-hint>
            <mat-error>{{ errorText(form.controls.appScanId, appScanHelp) }}</mat-error>
          </mat-form-field>
          @if (existing) {
            <p class="note">
              Built with {{ toolLabel() }}, runs on {{ targetLabel() }}. How it is built and
              deployed is changed in Product Management.
            </p>
          }
        } @else {
          <h3>What builds the code?</h3>
          <dso-choice-tiles label="Build tool" [options]="tools" [(value)]="tool" />
          @if (checked() && tool() === null) {
            <p class="choice-error">Choose Gradle or Maven</p>
          }
          @if (deploys) {
            <h3>Where does it run?</h3>
            <dso-choice-tiles label="Runs on" [options]="targets" [(value)]="target" />
            @if (checked() && target() === null) {
              <p class="choice-error">Choose where the service runs</p>
            }
            @if (target() === 'OPENSHIFT') {
              <mat-form-field class="full-width project">
                <mat-label>OpenShift project</mat-label>
                <input
                  matInput
                  class="mono"
                  formControlName="openShiftProject"
                  placeholder="pay-payhub"
                  autocomplete="off"
                />
                <mat-hint>The name of its projects without -rd or -qc at the end</mat-hint>
                <mat-error>{{ errorText(form.controls.openShiftProject, projectHelp) }}</mat-error>
              </mat-form-field>
            }
          }
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        @if (page() === 2) {
          <button mat-button type="button" (click)="page.set(1)">Back</button>
        } @else {
          <button mat-button type="button" mat-dialog-close>Cancel</button>
        }
        <button mat-flat-button type="submit">{{ submitLabel() }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-dialog-content {
      display: flex;
      flex-direction: column;
      gap: 6px;
      width: min(560px, 80vw);
    }

    .page-count {
      margin: 0 0 6px;
      color: var(--dso-muted);
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    h3 {
      margin: 4px 0 2px;
      font-size: 14px;
    }

    .project {
      margin-top: 10px;
    }

    .note {
      margin: 4px 0 0;
      color: var(--dso-muted);
      font-size: 12px;
    }

    .choice-error {
      margin-top: 2px;
    }
  `,
})
export class ServiceDialog {
  protected readonly data = inject<ServiceDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ServiceDialog, WizardService>>(MatDialogRef);

  private readonly start = this.data.service;
  protected readonly existing = this.start?.id != null;
  protected readonly deploys = this.data.pipeline !== 'SAST';
  protected readonly tools = TOOLS;
  protected readonly targets = TARGETS;
  protected readonly nameHelp = NAME_HELP;
  protected readonly appScanHelp = APP_SCAN_HELP;
  protected readonly projectHelp = PROJECT_HELP;
  protected readonly errorText = errorText;

  protected readonly page = signal<1 | 2>(1);
  protected readonly checked = signal(false);
  protected readonly tool = signal<BuildTool | null>(this.start?.tool ?? null);
  protected readonly target = signal<DeployTarget | null>(this.start?.target ?? null);
  protected readonly toolLabel = computed(() => choiceLabel(TOOLS, this.tool()));
  protected readonly targetLabel = computed(() => choiceLabel(TARGETS, this.target()));
  protected readonly submitLabel = computed(() =>
    !this.existing && this.page() === 1 ? 'Next' : this.start ? 'Save service' : 'Add service',
  );

  protected readonly form = new FormGroup({
    name: text(
      this.start?.name,
      filled,
      Validators.pattern(SERVICE_NAME),
      max(100),
      (control: AbstractControl) => this.unique(control),
    ),
    description: text(this.start?.description, max(2000)),
    appScanId: text(this.start?.appScanId, filled, Validators.pattern(UUID)),
    openShiftProject: text(this.start?.openShiftProject, Validators.pattern(OPENSHIFT_PROJECT)),
  });

  protected next(): void {
    const { name, description, appScanId, openShiftProject } = this.form.controls;
    if (this.page() === 1) {
      [name, description, appScanId].forEach((control) => control.markAsTouched());
      if (!name.valid || !description.valid || !appScanId.valid) {
        return;
      }
      if (this.existing) {
        this.finish();
      } else {
        this.page.set(2);
      }
      return;
    }
    this.checked.set(true);
    openShiftProject.markAsTouched();
    if (this.tool() === null || (this.deploys && this.target() === null)) {
      return;
    }
    if (this.needsProject() && !openShiftProject.value.trim()) {
      openShiftProject.setErrors({ required: true });
    }
    if (!this.needsProject() || openShiftProject.valid) {
      this.finish();
    }
  }

  private needsProject(): boolean {
    return this.deploys && this.target() === 'OPENSHIFT';
  }

  private finish(): void {
    const value = this.form.getRawValue();
    this.dialogRef.close({
      id: this.start?.id ?? null,
      name: value.name.trim(),
      description: value.description.trim(),
      appScanId: value.appScanId.trim().toLowerCase(),
      tool: this.tool()!,
      target: this.target(),
      openShiftProject: this.target() === 'OPENSHIFT' ? value.openShiftProject.trim() : '',
    });
  }

  private unique(control: AbstractControl): ValidationErrors | null {
    const name = String(control.value ?? '')
      .trim()
      .toLowerCase();
    return this.data.takenNames.some((taken) => taken.toLowerCase() === name)
      ? { rule: 'Another service of this product already has this name' }
      : null;
  }
}
