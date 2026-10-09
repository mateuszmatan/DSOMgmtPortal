import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RunResult } from '../core/models';
import { RUN_LOOK } from '../shared/status-chip';

export const STATUS_ORDER = (
  ['FAILURE', 'UNSTABLE', 'ABORTED', 'NOT_BUILT', 'SUCCESS', 'NO_DATA', 'DISABLED'] as RunResult[]
).map((status) => ({ status, label: RUN_LOOK[status].label }));

@Component({
  selector: 'dso-status-bar',
  imports: [MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bar" role="img" [attr.aria-label]="summary()">
      @for (segment of segments(); track segment.status) {
        <span
          class="segment swatch"
          [class]="segment.status.toLowerCase()"
          [style.flex-grow]="segment.count"
          [matTooltip]="segment.count + ' ' + segment.label.toLowerCase()"
        ></span>
      } @empty {
        <span class="segment swatch no_data" style="flex-grow: 1"></span>
      }
    </div>
  `,
  styles: `
    .bar {
      display: flex;
      gap: 2px;
      height: 8px;
      overflow: hidden;
      background: var(--dso-neutral-bg);
    }
    .segment {
      flex-basis: 0;
      min-width: 6px;
    }
  `,
})
export class StatusBar {
  readonly counts = input.required<Partial<Record<RunResult, number>>>();

  protected readonly segments = computed(() =>
    STATUS_ORDER.map((entry) => ({ ...entry, count: this.counts()[entry.status] ?? 0 })).filter(
      (entry) => entry.count > 0,
    ),
  );
  protected readonly summary = computed(
    () =>
      this.segments()
        .map((segment) => `${segment.count} ${segment.label.toLowerCase()}`)
        .join(', ') || 'No pipelines',
  );
}
