import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { GlobalSettings } from '../core/models';
import { addItem } from '../shared/form-controls';
import { Field, Fields, formRevision } from '../shared/fields';
import {
  ServiceForm,
  UrbanCodeApplicationForm,
  createUrbanCodeApplicationForm,
  createUrbanCodeComponentForm,
} from './product-form-model';

const SWITCHES: Field[] = [
  { key: 'deployWithSnapshot', label: 'Deploy with a snapshot', code: 'deployWithSnapshot' },
  {
    key: 'includeOnlyDeployVersions',
    label: 'Snapshot holds only the deployed versions',
    code: 'includeOnlyDeployVersions',
  },
  {
    key: 'updateSnapshotComponents',
    label: "Update the snapshot's components",
    code: 'updateSnapshotComp',
  },
  { key: 'deployOnlyChanged', label: 'Deploy only changed versions', code: 'deployOnlyChanged' },
].map((field) => ({ ...field, kind: 'check' as const, span: 6 }));

const TEXTS: Field[] = [
  {
    key: 'skipWait',
    kind: 'check',
    label: 'Do not wait for the deployment result',
    code: 'skipWait',
  },
  {
    key: 'deployDescription',
    kind: 'area',
    label: 'Deployment description',
    code: 'deploy.vm.dod.deployDescription',
  },
  {
    key: 'requestProperties',
    kind: 'area',
    label: 'Request properties',
    mono: true,
    code: 'deploy.vm.dod.requestProperties',
  },
];

const APPLICATION: Field[] = [
  {
    key: 'applicationName',
    label: 'Application name',
    span: 5,
    mono: true,
    code: 'applicationName',
  },
  { key: 'order', kind: 'number', label: 'Order', span: 2, min: 1, max: 999, code: 'order' },
  {
    key: 'environments',
    label: 'Environments',
    span: 5,
    mono: true,
    placeholder: 'DV, RD',
    code: 'environments',
    hint: 'left empty: all of them',
  },
  { key: 'snapshotName', label: 'Snapshot name', code: 'snapshotName' },
];

const COMPONENT: Field[] = [
  { key: 'componentName', label: 'Component name', span: 4, mono: true, code: 'componentName' },
  {
    key: 'baseDir',
    label: 'Base folder',
    span: 4,
    mono: true,
    placeholder: 'build/libs',
    code: 'baseDir',
  },
  { key: 'versionPrefix', label: 'Version prefix', span: 2, mono: true, code: 'versionPrefix' },
  { key: 'version', label: 'Version', span: 2, mono: true, code: 'version' },
  {
    key: 'fileIncludePatterns',
    label: 'Files to include',
    mono: true,
    placeholder: '*.jar',
    code: 'fileIncludePatterns',
  },
  {
    key: 'fileExcludePatterns',
    label: 'Files to exclude',
    mono: true,
    code: 'fileExcludePatterns',
  },
  {
    key: 'incrementalVersion',
    kind: 'check',
    label: 'Incremental version',
    code: 'incrementalVersion',
  },
];

@Component({
  selector: 'dso-urban-code-fields',
  imports: [ReactiveFormsModule, MatButtonModule, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './urban-code-fields.html',
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    .component {
      position: relative;
      margin-top: 6px;
      padding: 8px 88px 6px 10px;
      border: 1px solid var(--dso-border);
      background: #fbfcfd;

      .remove {
        position: absolute;
        top: 6px;
        right: 4px;
      }
    }
    .add-component {
      margin-top: 6px;
    }
    @media (max-width: 700px) {
      .component {
        padding: 36px 10px 6px;
      }
    }
  `,
})
export class UrbanCodeFields {
  readonly form = input.required<ServiceForm>();
  readonly defaults = input<GlobalSettings | null>(null);

  private readonly changes = formRevision(this.form);

  protected readonly applications = computed(() => {
    this.changes();
    return [...this.form().controls.urbanCodeApplications.controls];
  });

  protected readonly applicationFields = APPLICATION;
  protected readonly componentFields = COMPONENT;

  protected settingsFields(): Field[] {
    const deployment = this.defaults()?.deployment;
    return [
      {
        key: 'siteName',
        label: 'Site name',
        placeholder: deployment?.urbanCodeSiteName ?? '',
        code: 'deploy.vm.dod.siteName',
        hint: `left empty: ${deployment?.urbanCodeSiteName ?? 'the global default'}`,
      },
      {
        key: 'deployProcess',
        label: 'Deployment process',
        placeholder: deployment?.urbanCodeDeployProcess ?? '',
        code: 'deploy.vm.dod.deployProcess',
        hint: `left empty: ${deployment?.urbanCodeDeployProcess ?? 'the global default'}`,
      },
      ...SWITCHES,
      ...TEXTS,
    ];
  }

  protected addApplication(): void {
    addItem(this.form().controls.urbanCodeApplications, createUrbanCodeApplicationForm());
    this.form().markAsDirty();
  }

  protected removeApplication(index: number): void {
    this.form().controls.urbanCodeApplications.removeAt(index);
    this.form().markAsDirty();
  }

  protected addComponent(application: UrbanCodeApplicationForm): void {
    addItem(application.controls.components, createUrbanCodeComponentForm());
    this.form().markAsDirty();
  }

  protected removeComponent(application: UrbanCodeApplicationForm, index: number): void {
    application.controls.components.removeAt(index);
    this.form().markAsDirty();
  }
}
