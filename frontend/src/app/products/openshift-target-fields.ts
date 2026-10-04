import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { REGIONS, Region } from '../core/models';
import { errorText } from '../shared/form-errors';
import { OpenShiftTargetForm, ServiceForm } from './product-form-model';

type TargetKey = keyof OpenShiftTargetForm['controls'];

interface TargetField {
  key: Exclude<TargetKey, 'skipConfigDeploy'>;
  label: string;
  config: string;
  span: number;
  placeholder?: string;
  pattern?: string;
}

const IMAGE_BUILD: TargetField[] = [
  { key: 'projectBuild', label: 'Build project', config: 'projectBuildR', span: 4 },
  {
    key: 'buildConfigPath',
    label: 'BuildConfig file',
    config: 'buildConfigPath',
    span: 4,
    placeholder: 'openshift/buildconfig.yaml',
  },
  {
    key: 'dockerFilePath',
    label: 'Dockerfile',
    config: 'dockerFilePath',
    span: 4,
    placeholder: 'openshift/Dockerfile',
  },
  {
    key: 'buildContext',
    label: 'Build context',
    config: 'buildContext',
    span: 6,
    placeholder: 'target/docker',
  },
  { key: 'addFile', label: 'File added to the image', config: 'addFile', span: 6 },
];

const REGISTRY: TargetField[] = [
  { key: 'dockerRepoPush', label: 'Image pushed to', config: 'qcDockerRepoPush', span: 6 },
  { key: 'dockerRepoPull', label: 'Image pulled from', config: 'qcDockerRepoPull', span: 6 },
  { key: 'certDir', label: 'OpenShift certificates folder', config: 'openshiftCertDir', span: 6 },
  { key: 'nexusAuthFile', label: 'Nexus auth file', config: 'nexus.authfile', span: 6 },
];

const DEPLOYMENT: TargetField[] = [
  { key: 'projectDeployment', label: 'Deployment project', config: 'projectDeploymentR', span: 4 },
  {
    key: 'deployConfigPath',
    label: 'Deployment file',
    config: 'deployConfigPath',
    span: 4,
    placeholder: 'openshift/deployment.yaml',
  },
  { key: 'configPath', label: 'Configuration file', config: 'configPathR', span: 4 },
  {
    key: 'healthCheckUrl',
    label: 'Health check path',
    config: 'healthCheckUrl',
    span: 4,
    placeholder: '/actuator/health',
  },
  { key: 'routeHostname', label: 'Route host name', config: 'routeHostnameR', span: 4 },
  { key: 'deploymentPath', label: 'Deployment path', config: 'deploymentPath', span: 4 },
];

const REPOSITORY: TargetField[] = [
  {
    key: 'deploymentRepoUrl',
    label: 'Repository URL',
    config: 'deploymentRepo.url',
    span: 6,
    pattern: 'Must be an http, https, ssh or git@ URL',
  },
  { key: 'deploymentRepoBranch', label: 'Branch', config: 'deploymentRepo.branch', span: 3 },
  {
    key: 'deploymentRepoCredentialsId',
    label: 'Credentials ID',
    config: 'deploymentRepo.credentials',
    span: 3,
  },
];

@Component({
  selector: 'dso-openshift-target-fields',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSlideToggleModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <mat-button-toggle-group
      class="regions"
      [value]="region()"
      (change)="region.set($event.value)"
      aria-label="Region"
      hideSingleSelectionIndicator
    >
      @for (option of regions; track option) {
        <mat-button-toggle [value]="option">
          {{ option }} region
          @if (target(option).invalid) {
            <mat-icon class="problem">error</mat-icon>
          }
        </mat-button-toggle>
      }
    </mat-button-toggle-group>
    <p class="region-note">
      @if (region() === 'RD') {
        The lower test region, where the run builds the image. Keys under
        <code>deploy.openshift.rd</code>.
      } @else {
        The higher test region, which deploys the image the run built. Keys under
        <code>deploy.openshift.qc</code>.
      }
    </p>

    @let t = target(region());
    <div class="form-fields">
      @if (region() === 'RD' || hasImageBuild(t)) {
        <h5 class="sub-heading span-12">Image build</h5>
        @for (field of imageBuild; track field.key) {
          <ng-container *ngTemplateOutlet="textField; context: { $implicit: field, t }" />
        }
      }
      <h5 class="sub-heading span-12">Image registry</h5>
      @for (field of registry; track field.key) {
        <ng-container *ngTemplateOutlet="textField; context: { $implicit: field, t }" />
      }
      <h5 class="sub-heading span-12">Deployment</h5>
      @for (field of deployment; track field.key) {
        <ng-container *ngTemplateOutlet="textField; context: { $implicit: field, t }" />
      }
      <mat-slide-toggle class="span-12" [formControl]="t.controls.skipConfigDeploy"
        >Skip deploying the configuration (<code>skipConfigDeploy</code>)</mat-slide-toggle
      >
      <h5 class="sub-heading span-12">Deployment repository</h5>
      @for (field of repository; track field.key) {
        <ng-container *ngTemplateOutlet="textField; context: { $implicit: field, t }" />
      }
    </div>

    <ng-template #textField let-field let-t="t">
      <mat-form-field [class]="'span-' + field.span">
        <mat-label>{{ field.label }}</mat-label>
        <input
          matInput
          [formControl]="t.controls[field.key]"
          [placeholder]="field.placeholder ?? ''"
          class="mono"
        />
        <mat-hint
          ><code>{{ field.config }}</code></mat-hint
        >
        <mat-error>{{ errorText(t.controls[field.key], field.pattern) }}</mat-error>
      </mat-form-field>
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    .regions mat-icon {
      margin-left: 6px;
      font-size: 16px;
      width: 16px;
      height: 16px;
      vertical-align: middle;
      color: var(--dso-danger);
    }
    .region-note {
      margin: 10px 0 4px;
      font-size: 12.5px;
      color: var(--dso-muted);
    }
    mat-slide-toggle {
      padding: 4px 0;
    }
  `,
})
export class OpenShiftTargetFields {
  readonly form = input.required<ServiceForm>();

  protected readonly regions = REGIONS;
  protected readonly region = signal<Region>('RD');
  protected readonly imageBuild = IMAGE_BUILD;
  protected readonly registry = REGISTRY;
  protected readonly deployment = DEPLOYMENT;
  protected readonly repository = REPOSITORY;
  protected readonly errorText = errorText;

  protected target(region: Region): OpenShiftTargetForm {
    return this.form().controls.openShiftTargets.controls[region];
  }

  protected hasImageBuild(target: OpenShiftTargetForm): boolean {
    return IMAGE_BUILD.some((field) => !!target.controls[field.key].value.trim());
  }
}
