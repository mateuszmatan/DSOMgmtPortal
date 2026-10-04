import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RunResult } from '../core/models';

export const STATUS_ORDER: { status: RunResult; label: string }[] = [
  { status: 'FAILURE', label: 'Failed' },
  { status: 'UNSTABLE', label: 'Unstable' },
  { status: 'ABORTED', label: 'Aborted' },
  { status: 'NOT_BUILT', label: 'Not built' },
  { status: 'SUCCESS', label: 'Success' },
  { status: 'NO_DATA', label: 'No runs yet' },
  { status: 'DISABLED', label: 'Key invalidated' },
];

@Component({
  selector: 'dso-status-bar',
  imports: [MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bar" role="img" [attr.aria-label]="summary()">
      @for (segment of segments(); track segment.status) {
        <span
          class="segment"
          [class]="segment.status.toLowerCase()"
          [style.flex-grow]="segment.count"
          [matTooltip]="segment.count + ' ' + segment.label.toLowerCase()"
        ></span>
      } @empty {
        <span class="segment no_data" style="flex-grow: 1"></span>
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
    .success {
      background: #34a853;
    }
    .unstable {
      background: #f9ab00;
    }
    .failure {
      background: #d93025;
    }
    .aborted,
    .not_built {
      background: #9aa0a6;
    }
    .no_data {
      background: #dadce0;
    }
    .disabled {
      background: repeating-linear-gradient(135deg, #f28b82 0 4px, #fce8e6 4px 8px);
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
