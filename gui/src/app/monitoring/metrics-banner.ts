import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MonitoringStatus } from '../core/models';

@Component({
  selector: 'dso-metrics-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (status(); as s) {
      @if (!s.influxConfigured) {
        <div class="banner info">
          <span>
            InfluxDB is not configured, so the portal shows only the state of each pipeline's key.
            Set
            <code>INFLUX_URL</code> and <code>INFLUX_TOKEN</code> to read the runs the pipelines
            report.
          </span>
        </div>
      } @else if (!s.influxReachable) {
        <div class="banner">
          <span>InfluxDB cannot be reached: {{ s.influxError }}</span>
        </div>
      }
    }
    @if (metricsError()) {
      <div class="banner">
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
