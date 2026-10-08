import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  PipelineEvidence,
  ProductEvidence,
  ServiceEvidence,
  StageEvidence,
  pipelineTypeLabel,
} from '../core/models';
import { Notifier } from '../core/notifier';
import { CheckChip } from '../shared/check-chip';
import { CountedPipe, DurationPipe, durationOrNull } from '../shared/formatting';
import { StatusChip } from '../shared/status-chip';
import {
  SUITE_LABELS,
  evidenceText,
  formatPercent,
  formatUtc,
  goldenFixResult,
  goldenFixUpgrades,
  hasFindings,
  scanRows,
  stageDetails,
  suiteRows,
} from './evidence-text';

@Component({
  selector: 'dso-pipeline-evidence-card',
  imports: [
    ClipboardModule,
    MatButtonModule,
    MatTooltipModule,
    CheckChip,
    CountedPipe,
    DurationPipe,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-evidence-card.html',
  styleUrl: './pipeline-evidence-card.scss',
})
export class PipelineEvidenceCard {
  readonly product = input.required<ProductEvidence>();
  readonly service = input.required<ServiceEvidence>();
  readonly pipeline = input.required<PipelineEvidence>();

  private readonly notifier = inject(Notifier);

  protected readonly typeLabel = computed(() => pipelineTypeLabel(this.pipeline().type));
  protected readonly text = computed(() =>
    evidenceText(this.product(), this.service(), this.pipeline()),
  );
  protected readonly buildFacts = computed(() => {
    const build = this.pipeline().run?.build;
    return build
      ? [
          { label: 'Finished', value: formatUtc(build.finishedAt), mono: false, title: '' },
          { label: 'Branch', value: build.branch, mono: true, title: '' },
          {
            label: 'Commit',
            value: build.commit?.slice(0, 12) ?? null,
            mono: true,
            title: build.commit ?? '',
          },
          { label: 'Artifact version', value: build.artifactVersion, mono: true, title: '' },
          {
            label: 'Duration',
            value: durationOrNull(build.durationSeconds),
            mono: false,
            title: '',
          },
          {
            label: 'Config rendered',
            value: formatUtc(build.configRenderedAt),
            mono: false,
            title: 'When the portal rendered the configuration this build read',
          },
          { label: 'Config sha256', value: build.configSha256, mono: true, title: '' },
        ]
      : [];
  });
  protected readonly suites = computed(() => {
    const run = this.pipeline().run;
    return run ? suiteRows(run) : [];
  });
  protected readonly scans = computed(() => {
    const run = this.pipeline().run;
    return run ? scanRows(run) : [];
  });

  protected readonly suiteLabels = SUITE_LABELS;
  protected readonly percent = formatPercent;
  protected readonly hasFindings = hasFindings;
  protected readonly goldenFixResult = goldenFixResult;
  protected readonly goldenFixUpgrades = goldenFixUpgrades;

  protected over(value: number | null, max: number | null): boolean {
    return value !== null && max !== null && value > max;
  }

  protected stageTip(stage: StageEvidence): string {
    return stageDetails(stage).join(' · ');
  }

  protected copied(success: boolean): void {
    if (success) {
      this.notifier.success('Evidence copied for ServiceNow');
    } else {
      this.notifier.error(new Error('The evidence could not be copied to the clipboard.'));
    }
  }
}
