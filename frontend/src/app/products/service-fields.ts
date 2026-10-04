import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import {
  BuildTool,
  FlutterPlatform,
  GlobalSettings,
  REGIONS,
  Region,
} from '../core/models';
import { errorText } from '../shared/form-errors';
import { GoldenFixFields } from './golden-fix-fields';
import { OpenShiftTargetFields } from './openshift-target-fields';
import {
  ServiceForm,
  ServiceSectionId,
  firstInvalidSection,
  sectionInvalid,
  sectionTouched,
  visibleSections,
} from './product-form-model';
import { TestJobsFields } from './test-jobs-fields';
import { ToolCommandFields } from './tool-command-fields';
import { UrbanCodeFields } from './urban-code-fields';

const REGION_NAMES: Record<Region, string> = {
  RD: 'RD, the lower test region',
  QC: 'QC, the higher test region',
};

/**
 * Every setting of one service, in sections that follow its config.yaml entry: a rail lists the sections that
 * apply to the service's build tool and deployment target and marks those holding a problem. It is checked
 * with its parent so that changes the parent makes to the form, such as errors the API reported, show up at
 * once.
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
    GoldenFixFields,
    OpenShiftTargetFields,
    TestJobsFields,
    ToolCommandFields,
    UrbanCodeFields,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './service-fields.html',
  styleUrl: './service-fields.scss',
})
export class ServiceFields {
  readonly form = input.required<ServiceForm>();
  readonly productCode = input('');
  /** The global settings, for the values a blank field falls back to; null while unknown. */
  readonly defaults = input<GlobalSettings | null>(null);
  /** Whether the product was submitted, after which every problem is marked. */
  readonly submitted = input(false);

  private readonly selected = signal<ServiceSectionId>('general');

  protected readonly buildTools: { value: BuildTool; label: string }[] = [
    { value: 'GRADLE', label: 'Gradle' },
    { value: 'MAVEN', label: 'Maven' },
    { value: 'FLUTTER', label: 'Flutter' },
  ];
  protected readonly flutterPlatforms: FlutterPlatform[] = [
    'APK',
    'APPBUNDLE',
    'IOS',
    'MACOS',
    'LINUX',
    'WINDOWS',
    'WEB',
  ];
  protected readonly regions = REGIONS;
  protected readonly regionNames = REGION_NAMES;
  protected readonly errorText = errorText;

  /** The sections of the rail, each marked when it holds a problem the user should see. */
  protected sections() {
    const form = this.form();
    return visibleSections(form).map((section) => ({
      ...section,
      problem: sectionInvalid(form, section) && (this.submitted() || sectionTouched(form, section)),
    }));
  }

  /** The section shown; one that no longer applies, after the build tool changed, falls back to General. */
  protected current(): ServiceSectionId {
    const id = this.selected();
    return visibleSections(this.form()).some((section) => section.id === id) ? id : 'general';
  }

  protected select(id: ServiceSectionId): void {
    this.selected.set(id);
  }

  /** Shows the first section holding an invalid value; false when there is none. */
  revealFirstProblem(): boolean {
    const id = firstInvalidSection(this.form());
    if (id) {
      this.selected.set(id);
    }
    return id !== null;
  }

  protected tool(): BuildTool {
    return this.form().controls.build.controls.tool.value;
  }

  protected isVm(): boolean {
    return this.form().controls.deployment.controls.target.value === 'VM';
  }

  /** The metrics project tag the API fills in when none is given. */
  protected defaultProject(): string {
    return `${this.productCode() || 'CODE'}-${this.form().controls.name.value || 'service'}`;
  }

  /** The hint part naming the value a blank field falls back to. */
  protected fallback(value: string | number | null | undefined): string {
    return value === null || value === undefined || value === ''
      ? ''
      : ` · left empty: ${value}`;
  }

  /** What a service inheriting the GoldenFix policy gets. */
  protected inheritedGoldenFix(): string {
    const intro = 'The service follows the GoldenFix defaults of the DevSecOps Global Settings';
    const g = this.defaults()?.goldenFix;
    if (!g) {
      return `${intro}.`;
    }
    const dependencies = g.onlyDirectDependencies
      ? 'direct dependencies only'
      : 'direct and transitive dependencies';
    const verification = g.verifyEnabled
      ? 'each fix verified by a build'
      : 'without a verification build';
    return (
      `${intro}: ${g.ecosystems.join(', ')}, threat level ${g.minThreatLevel} and above, ` +
      `${dependencies}, ${verification}.`
    );
  }

  protected sshHost(region: Region): string | undefined {
    const deployment = this.defaults()?.deployment;
    return region === 'RD' ? deployment?.rdHost : deployment?.qcHost;
  }
}
