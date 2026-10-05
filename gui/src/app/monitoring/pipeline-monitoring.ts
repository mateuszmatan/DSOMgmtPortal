import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MonitoringApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { DoraSummary, PIPELINE_TYPES, PipelineMonitoring, PipelineType } from '../core/models';
import { DoraLevelBadge } from '../shared/dora-level';
import { DurationPipe, RelativeTimePipe, formatDuration } from '../shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { ActivityChart } from './activity-chart';
import { MetricsBanner } from './metrics-banner';

export const RANGES = ['7d', '30d', '90d', '180d'];

const moment = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

@Component({
  selector: 'dso-pipeline-monitoring',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    ActivityChart,
    DoraLevelBadge,
    DurationPipe,
    MetricsBanner,
    RelativeTimePipe,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-monitoring.html',
  styleUrl: './pipeline-monitoring.scss',
})
export class PipelineMonitoringPage {
  readonly id = input.required<string>();
  readonly range = input<string>();

  private readonly api = inject(MonitoringApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly ranges = RANGES;
  protected readonly selectedRange = computed(() => {
    const range = this.range();
    return range && RANGES.includes(range) ? range : '30d';
  });

  protected readonly resource = rxResource({
    params: () => ({ id: Number(this.id()), range: this.selectedRange() }),
    stream: ({ params }) => this.api.pipeline(params.id, params.range),
  });
  protected readonly data = linkedSignal<
    PipelineMonitoring | undefined,
    PipelineMonitoring | undefined
  >({
    source: () => (this.resource.hasValue() ? this.resource.value() : undefined),
    computation: (next, previous) => next ?? previous?.value,
  });

  protected readonly tiles = computed(() => {
    const dora = this.data()?.dora;
    return dora ? doraTiles(dora) : [];
  });

  protected readonly panels = computed<
    { id: number; title: string; width: number; url: SafeResourceUrl }[]
  >(() =>
    (this.data()?.grafana?.panels ?? [])
      .filter((panel) => /^https?:\/\//.test(panel.url))
      .map((panel) => ({
        ...panel,
        url: this.sanitizer.bypassSecurityTrustResourceUrl(panel.url),
      })),
  );

  protected readonly runColumns = [
    'time',
    'result',
    'build',
    'branch',
    'commit',
    'duration',
    'stages',
  ];
  protected readonly errorMessage = errorMessage;

  protected typeLabel(type: PipelineType): string {
    return PIPELINE_TYPES.find((option) => option.value === type)?.label ?? type;
  }

  protected selectRange(range: string): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { range }, replaceUrl: true });
  }
}

export interface DoraTile {
  title: string;
  value: string;
  detail: string;
  level: DoraSummary['deploymentFrequencyLevel'];
  alert?: string;
}

export function doraTiles(dora: DoraSummary): DoraTile[] {
  return [
    {
      title: 'Deployment frequency',
      value: frequency(dora.deploymentsPerWeek),
      detail: `${dora.deployments} ${dora.deployments === 1 ? 'deployment' : 'deployments'} in ${dora.rangeDays} days`,
      level: dora.deploymentFrequencyLevel,
    },
    {
      title: 'Lead time for changes',
      value: formatDuration(dora.leadTimeMedianSeconds),
      detail: 'Median from commit to deployment',
      level: dora.leadTimeLevel,
    },
    {
      title: 'Change failure rate',
      value:
        dora.changeFailureRatePercent === null
          ? '–'
          : `${dora.changeFailureRatePercent.toFixed(1)}%`,
      detail: `Of ${dora.runs} ${dora.runs === 1 ? 'run' : 'runs'} in the range`,
      level: dora.changeFailureRateLevel,
    },
    {
      title: 'Time to restore',
      value: formatDuration(dora.meanTimeToRestoreSeconds),
      detail: `Mean of ${dora.restores} ${dora.restores === 1 ? 'recovery' : 'recoveries'} from a failure`,
      level: dora.timeToRestoreLevel,
      alert: dora.failingSince
        ? `Failing since ${moment.format(new Date(dora.failingSince))}`
        : undefined,
    },
  ];
}

function frequency(perWeek: number | null): string {
  if (perWeek === null) {
    return '–';
  }
  if (perWeek >= 7) {
    return `${(perWeek / 7).toFixed(1)} / day`;
  }
  if (perWeek >= 1) {
    return `${perWeek.toFixed(1)} / week`;
  }
  return `${((perWeek * 30) / 7).toFixed(1)} / month`;
}
