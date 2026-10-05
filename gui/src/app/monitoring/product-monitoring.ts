import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { MonitoringApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { PIPELINE_TYPES, PipelineHealth, PipelineType, RunResult } from '../core/models';
import { DurationPipe, RelativeTimePipe } from '../shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { MetricsBanner } from './metrics-banner';
import { StatusBar } from './status-bar';

@Component({
  selector: 'dso-product-monitoring',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    DurationPipe,
    MetricsBanner,
    RelativeTimePipe,
    StatusBar,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-monitoring.html',
  styleUrl: './product-monitoring.scss',
})
export class ProductMonitoringPage {
  readonly id = input.required<string>();

  private readonly api = inject(MonitoringApi);
  private readonly router = inject(Router);

  protected readonly product = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.product(params),
  });

  protected readonly counts = computed(() => {
    const counts: Partial<Record<RunResult, number>> = {};
    if (this.product.hasValue()) {
      for (const health of this.product.value().pipelines) {
        counts[health.status] = (counts[health.status] ?? 0) + 1;
      }
    }
    return counts;
  });

  protected readonly columns = [
    'service',
    'type',
    'status',
    'lastRun',
    'duration',
    'stages',
    'jenkins',
  ];
  protected readonly errorMessage = errorMessage;

  protected typeLabel(type: PipelineType): string {
    return PIPELINE_TYPES.find((option) => option.value === type)?.label ?? type;
  }

  protected open(health: PipelineHealth): void {
    this.router.navigate(['/monitoring/pipelines', health.pipeline.id]);
  }
}
