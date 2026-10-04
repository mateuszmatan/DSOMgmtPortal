import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RunResult } from '../core/models';

const LOOK: Record<RunResult, { label: string; icon: string; tone: string }> = {
  SUCCESS: { label: 'Success', icon: 'check_circle', tone: 'success' },
  UNSTABLE: { label: 'Unstable', icon: 'error', tone: 'warning' },
  FAILURE: { label: 'Failed', icon: 'cancel', tone: 'danger' },
  ABORTED: { label: 'Aborted', icon: 'block', tone: 'neutral' },
  NOT_BUILT: { label: 'Not built', icon: 'remove_circle_outline', tone: 'neutral' },
  NO_DATA: { label: 'No runs yet', icon: 'hourglass_empty', tone: 'neutral' },
  DISABLED: { label: 'Key invalidated', icon: 'key_off', tone: 'danger-outline' },
};

/** Coloured pill showing the outcome of a pipeline. */
@Component({
  selector: 'dso-status-chip',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="look().tone"><mat-icon>{{ look().icon }}</mat-icon>{{ label() ?? look().label }}</span>`,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 10px 2px 6px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      line-height: 20px;
      white-space: nowrap;
    }
    mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .success { background: var(--dso-success-bg); color: var(--dso-success); }
    .warning { background: var(--dso-warning-bg); color: var(--dso-warning); }
    .danger { background: var(--dso-danger-bg); color: var(--dso-danger); }
    .danger-outline { background: #fff; color: var(--dso-danger); box-shadow: inset 0 0 0 1px #f3b6b2; }
    .neutral { background: var(--dso-neutral-bg); color: var(--dso-neutral); }
  `,
})
export class StatusChip {
  readonly status = input.required<RunResult>();
  readonly label = input<string>();
  protected readonly look = computed(() => LOOK[this.status()] ?? LOOK.NO_DATA);
}
