import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { REGIONS, Region } from '../core/models';
import { Field, Fields, formRevision } from '../shared/fields';
import { OpenShiftTargetForm, ServiceForm } from './product-form-model';

const field = (
  key: string,
  label: string,
  code: string,
  span: number,
  placeholder = '',
): Field => ({
  key,
  label,
  code,
  span,
  placeholder,
  mono: true,
});

export const IMAGE_BUILD: Field[] = [
  field('projectBuild', 'Build project', 'projectBuildR', 4),
  field('buildConfigPath', 'BuildConfig file', 'buildConfigPath', 4, 'openshift/buildconfig.yaml'),
  field('dockerFilePath', 'Dockerfile', 'dockerFilePath', 4, 'openshift/Dockerfile'),
  field('buildContext', 'Build context', 'buildContext', 6, 'target/docker'),
  field('addFile', 'File added to the image', 'addFile', 6),
];

const REGISTRY: Field[] = [
  field('dockerRepoPush', 'Image pushed to', 'qcDockerRepoPush', 6),
  field('dockerRepoPull', 'Image pulled from', 'qcDockerRepoPull', 6),
  field('certDir', 'OpenShift certificates folder', 'openshiftCertDir', 6),
  field('nexusAuthFile', 'Nexus auth file', 'nexus.authfile', 6),
];

const DEPLOYMENT: Field[] = [
  field('projectDeployment', 'Deployment project', 'projectDeploymentR', 4),
  field('deployConfigPath', 'Deployment file', 'deployConfigPath', 4, 'openshift/deployment.yaml'),
  field('configPath', 'Configuration file', 'configPathR', 4),
  field('healthCheckUrl', 'Health check path', 'healthCheckUrl', 4, '/actuator/health'),
  field('routeHostname', 'Route host name', 'routeHostnameR', 4),
  field('deploymentPath', 'Deployment path', 'deploymentPath', 4),
  {
    key: 'skipConfigDeploy',
    kind: 'check',
    label: 'Skip deploying the configuration',
    code: 'skipConfigDeploy',
  },
];

const REPOSITORY: Field[] = [
  {
    ...field('deploymentRepoUrl', 'Repository URL', 'deploymentRepo.url', 6),
    error: 'Must be an http, https, ssh or git@ URL',
  },
  field('deploymentRepoBranch', 'Branch', 'deploymentRepo.branch', 3),
  field('deploymentRepoCredentialsId', 'Credentials ID', 'deploymentRepo.credentials', 3),
];

@Component({
  selector: 'dso-openshift-target-fields',
  imports: [MatButtonToggleModule, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <mat-button-toggle-group
      class="regions"
      [value]="region()"
      (change)="region.set($event.value)"
      aria-label="Region"
    >
      @for (option of regions; track option) {
        <mat-button-toggle [value]="option">
          {{ option }} region
          @if (target(option).invalid) {
            <span class="problem-mark" role="img" aria-label="Needs your attention"></span>
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
        <dso-fields [group]="t" [fields]="imageBuild" />
      }
      <h5 class="sub-heading span-12">Image registry</h5>
      <dso-fields [group]="t" [fields]="registry" />
      <h5 class="sub-heading span-12">Deployment</h5>
      <dso-fields [group]="t" [fields]="deployment" />
      <h5 class="sub-heading span-12">Deployment repository</h5>
      <dso-fields [group]="t" [fields]="repository" />
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    .regions .problem-mark {
      margin-left: 6px;
      vertical-align: middle;
    }
    .region-note {
      margin: 6px 0 2px;
      font-size: 12px;
      color: var(--dso-muted);
    }
  `,
})
export class OpenShiftTargetFields {
  readonly form = input.required<ServiceForm>();

  private readonly changes = formRevision(this.form);

  protected readonly regions = REGIONS;
  protected readonly region = signal<Region>('RD');
  protected readonly imageBuild = IMAGE_BUILD;
  protected readonly registry = REGISTRY;
  protected readonly deployment = DEPLOYMENT;
  protected readonly repository = REPOSITORY;

  protected target(region: Region): OpenShiftTargetForm {
    this.changes();
    return this.form().controls.openShiftTargets.controls[region];
  }

  protected hasImageBuild(target: OpenShiftTargetForm): boolean {
    return IMAGE_BUILD.some((field) => !!target.get(field.key)?.value.trim());
  }
}
