import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MonitoringApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { RunResult } from '../core/models';
import { MONITORING } from '../core/sections';
import { RelativeTimePipe } from '../shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { MetricsBanner } from './metrics-banner';
import { STATUS_ORDER, StatusBar } from './status-bar';

@Component({
  selector: 'dso-monitoring-overview',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
    MetricsBanner,
    RelativeTimePipe,
    StatusBar,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './monitoring-overview.html',
  styleUrl: './monitoring-overview.scss',
})
export class MonitoringOverview {
  private readonly api = inject(MonitoringApi);

  protected readonly section = MONITORING;
  protected readonly status = rxResource({ stream: () => this.api.status() });
  protected readonly overview = rxResource({ stream: () => this.api.overview() });

  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly query = toSignal(
    this.search.valueChanges.pipe(map((value) => value.trim().toLowerCase())),
    {
      initialValue: '',
    },
  );

  protected readonly products = computed(() => {
    if (!this.overview.hasValue()) {
      return [];
    }
    const query = this.query();
    return this.overview
      .value()
      .products.filter(
        (product) =>
          !query ||
          [product.name, product.code, product.ownerTeam ?? ''].some((value) =>
            value.toLowerCase().includes(query),
          ),
      );
  });

  protected readonly totals = computed(() => {
    const totals: Partial<Record<RunResult, number>> = {};
    if (this.overview.hasValue()) {
      for (const product of this.overview.value().products) {
        for (const [status, count] of Object.entries(product.statusCounts) as [
          RunResult,
          number,
        ][]) {
          totals[status] = (totals[status] ?? 0) + count;
        }
      }
    }
    return totals;
  });

  protected readonly tiles = computed(() => {
    const totals = this.totals();
    const pipelines = Object.values(totals).reduce((sum, count) => sum + (count ?? 0), 0);
    return [
      { label: 'Pipelines', value: pipelines, tone: 'info' },
      { label: 'Succeeded', value: totals.SUCCESS ?? 0, tone: 'success' },
      {
        label: 'Failing or unstable',
        value: (totals.FAILURE ?? 0) + (totals.UNSTABLE ?? 0),
        tone: 'danger',
      },
      { label: 'Keys invalidated', value: totals.DISABLED ?? 0, tone: 'neutral' },
    ];
  });

  protected readonly statusOrder = STATUS_ORDER;
  protected readonly errorMessage = errorMessage;

  protected refresh(): void {
    this.status.reload();
    this.overview.reload();
  }
}
