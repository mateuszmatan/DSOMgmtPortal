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
import { MatIconModule } from '@angular/material/icon';
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
import { jenkinsBuildUrl } from '../shared/jenkins';
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

/** One pipeline: its latest run, its DORA metrics over a time range and the Grafana panels of its tags. */
@Component({
  selector: 'dso-pipeline-monitoring',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
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
  /** The pipeline id, from the route. */
  readonly id = input.required<string>();
  /** The time range, from the query parameter of the same name. */
  readonly range = input<string>();

  private readonly api = inject(MonitoringApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly ranges = RANGES;
  protected readonly buildUrl = jenkinsBuildUrl;
  protected readonly selectedRange = computed(() => {
    const range = this.range();
    return range && RANGES.includes(range) ? range : '30d';
  });

  protected readonly resource = rxResource({
    params: () => ({ id: Number(this.id()), range: this.selectedRange() }),
    stream: ({ params }) => this.api.pipeline(params.id, params.range),
  });
  /** The latest data, kept on screen while another range loads. */
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
        // The URL is built by the portal from its own Grafana setting, not from user input.
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
  icon: string;
  value: string;
  detail: string;
  level: DoraSummary['deploymentFrequencyLevel'];
  alert?: string;
}

/** The four DORA metrics as tiles, each with its value, what it is based on and its performance level. */
export function doraTiles(dora: DoraSummary): DoraTile[] {
  return [
    {
      title: 'Deployment frequency',
      icon: 'rocket_launch',
      value: frequency(dora.deploymentsPerWeek),
      detail: `${dora.deployments} ${dora.deployments === 1 ? 'deployment' : 'deployments'} in ${dora.rangeDays} days`,
      level: dora.deploymentFrequencyLevel,
    },
    {
      title: 'Lead time for changes',
      icon: 'timer',
      value: formatDuration(dora.leadTimeMedianSeconds),
      detail: 'Median from commit to deployment',
      level: dora.leadTimeLevel,
    },
    {
      title: 'Change failure rate',
      icon: 'report',
      value:
        dora.changeFailureRatePercent === null
          ? '–'
          : `${dora.changeFailureRatePercent.toFixed(1)}%`,
      detail: `Of ${dora.runs} ${dora.runs === 1 ? 'run' : 'runs'} in the range`,
      level: dora.changeFailureRateLevel,
    },
    {
      title: 'Time to restore',
      icon: 'healing',
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
