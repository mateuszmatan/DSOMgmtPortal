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
import { DomSanitizer } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MonitoringApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { PipelineMonitoring, pipelineTypeLabel } from '../core/models';
import { BuildLink } from '../shared/build-link';
import { CountedPipe, DurationPipe, RelativeTimePipe } from '../shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { ActivityChart } from './activity-chart';
import { DoraTiles } from './dora-tiles';
import { MetricsBanner } from './metrics-banner';

const RANGES = ['7d', '30d', '90d', '180d'];

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
    BuildLink,
    CountedPipe,
    DoraTiles,
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

  protected readonly dashboard = computed(() => {
    const url = this.data()?.grafana?.dashboardUrl;
    return url ? this.sanitizer.bypassSecurityTrustResourceUrl(`${url}&kiosk`) : null;
  });

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

  protected readonly typeLabel = pipelineTypeLabel;

  protected selectRange(range: string): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { range }, replaceUrl: true });
  }
}
