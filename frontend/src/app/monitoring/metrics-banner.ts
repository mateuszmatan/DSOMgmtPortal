import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MonitoringStatus } from '../core/models';

/** Explains why statuses or metrics are missing: InfluxDB not configured, unreachable, or a failed query. */
@Component({
  selector: 'dso-metrics-banner',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (status(); as s) {
      @if (!s.influxConfigured) {
        <div class="banner info">
          <mat-icon>info</mat-icon>
          <span>
            InfluxDB is not configured, so the portal shows only the state of each pipeline's key.
            Set
            <code>INFLUX_URL</code> and <code>INFLUX_TOKEN</code> to read the runs the pipelines
            report.
          </span>
        </div>
      } @else if (!s.influxReachable) {
        <div class="banner">
          <mat-icon>cloud_off</mat-icon>
          <span>InfluxDB cannot be reached: {{ s.influxError }}</span>
        </div>
      }
    }
    @if (metricsError()) {
      <div class="banner">
        <mat-icon>warning_amber</mat-icon>
        <span
          >Pipeline metrics could not be read, so the statuses below may be incomplete:
          {{ metricsError() }}</span
        >
      </div>
    }
  `,
  styles: `
    code {
      font-size: 12px;
    }
  `,
})
export class MetricsBanner {
  readonly status = input<MonitoringStatus | undefined>();
  readonly metricsError = input<string | null>(null);
}
