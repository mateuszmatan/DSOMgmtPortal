import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RunResult } from '../core/models';

const LOOK: Record<RunResult, { label: string; tone: string }> = {
  SUCCESS: { label: 'Success', tone: 'success' },
  UNSTABLE: { label: 'Unstable', tone: 'warning' },
  FAILURE: { label: 'Failed', tone: 'danger' },
  ABORTED: { label: 'Aborted', tone: 'neutral' },
  NOT_BUILT: { label: 'Not built', tone: 'neutral' },
  NO_DATA: { label: 'No runs yet', tone: 'neutral' },
  DISABLED: { label: 'Key invalidated', tone: 'danger-outline' },
};

@Component({
  selector: 'dso-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="look().tone">{{ label() ?? look().label }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 0 7px;
      font-size: 11.5px;
      font-weight: 600;
      line-height: 18px;
      white-space: nowrap;
    }
    .success {
      background: var(--dso-success-bg);
      color: var(--dso-success);
    }
    .warning {
      background: var(--dso-warning-bg);
      color: var(--dso-warning);
    }
    .danger {
      background: var(--dso-danger-bg);
      color: var(--dso-danger);
    }
    .danger-outline {
      background: #fff;
      color: var(--dso-danger);
      box-shadow: inset 0 0 0 1px #f3b6b2;
    }
    .neutral {
      background: var(--dso-neutral-bg);
      color: var(--dso-neutral);
    }
  `,
})
export class StatusChip {
  readonly status = input.required<RunResult>();
  readonly label = input<string>();
  protected readonly look = computed(() => LOOK[this.status()] ?? LOOK.NO_DATA);
}
